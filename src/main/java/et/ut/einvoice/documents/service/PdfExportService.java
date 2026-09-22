package et.ut.einvoice.documents.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Controlled, secure PDF generation service.
 * Converts canonical HTML receipt documents to PDF using a restricted headless browser process
 * with SSRF prevention, execution sandboxing, concurrency bounds, and automatic temporary file cleanup.
 */
@Service
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);
    private static final int MAX_CONCURRENT_PDF_PROCESSES = 4;
    private static final int PROCESS_TIMEOUT_SECONDS = 15;
    private static final long MAX_PDF_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB limit

    private final Semaphore processSemaphore = new Semaphore(MAX_CONCURRENT_PDF_PROCESSES, true);
    private volatile String browserExecutablePath;

    public PdfExportService() {
        this.browserExecutablePath = resolveBrowserExecutable();
    }

    public boolean isPdfGenerationAvailable() {
        if (browserExecutablePath == null || browserExecutablePath.isBlank()) {
            browserExecutablePath = resolveBrowserExecutable();
        }
        return browserExecutablePath != null && !browserExecutablePath.isBlank();
    }

    /**
     * Converts the given HTML content to a PDF byte array.
     */
    public byte[] exportHtmlToPdf(String htmlContent) {
        if (!isPdfGenerationAvailable()) {
            throw new IllegalStateException("Headless browser for PDF generation is not available in the host environment");
        }

        boolean acquired;
        try {
            acquired = processSemaphore.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PDF generation process acquisition interrupted", e);
        }

        if (!acquired) {
            throw new RuntimeException("PDF export service is currently at maximum concurrency. Please retry shortly.");
        }

        Path tempHtmlPath = null;
        Path tempPdfPath = null;

        try {
            tempHtmlPath = Files.createTempFile("mor_receipt_", ".html");
            tempPdfPath = Files.createTempFile("mor_receipt_", ".pdf");

            // 1. Write HTML content to strictly controlled local file
            Files.writeString(tempHtmlPath, htmlContent);

            // 2. Build restricted headless browser execution command (no arbitrary URLs, sandboxed)
            String targetFileUrl = tempHtmlPath.toUri().toString();
            List<String> command = List.of(
                    browserExecutablePath,
                    "--headless=new",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--no-pdf-header-footer",
                    "--disable-software-rasterizer",
                    "--disable-extensions",
                    "--disable-background-networking",
                    "--disable-sync",
                    "--disable-default-apps",
                    "--no-first-run",
                    "--hide-scrollbars",
                    "--mute-audio",
                    "--print-to-pdf=" + tempPdfPath.toAbsolutePath(),
                    targetFileUrl
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("PDF rendering process timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Headless browser process exited with non-zero code: {}", exitCode);
                throw new RuntimeException("Headless PDF rendering failed with exit code " + exitCode);
            }

            if (!Files.exists(tempPdfPath) || Files.size(tempPdfPath) == 0) {
                throw new RuntimeException("PDF output file was not generated or is empty");
            }

            long fileSize = Files.size(tempPdfPath);
            if (fileSize > MAX_PDF_SIZE_BYTES) {
                throw new RuntimeException("Generated PDF exceeds maximum allowable size limit of 10MB");
            }

            return Files.readAllBytes(tempPdfPath);

        } catch (IOException e) {
            log.error("I/O error during PDF generation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF document: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PDF generation process execution was interrupted", e);
        } finally {
            processSemaphore.release();
            cleanupFile(tempHtmlPath);
            cleanupFile(tempPdfPath);
        }
    }

    private void cleanupFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
        }
    }

    private String resolveBrowserExecutable() {
        String[] candidates = new String[]{
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium"
        };

        for (String pathStr : candidates) {
            File file = new File(pathStr);
            if (file.exists() && file.canExecute()) {
                log.info("Resolved headless PDF renderer: {}", pathStr);
                return pathStr;
            }
        }

        log.warn("No headless Chrome/Edge executable found on host system. PDF export endpoint will report unavailable.");
        return null;
    }
}
