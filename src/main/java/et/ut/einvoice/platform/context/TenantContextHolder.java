package et.ut.einvoice.platform.context;

import et.ut.einvoice.platform.exception.BusinessException;

/**
 * ThreadLocal container ensuring strict tenant boundaries for the active request.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext getContext() {
        return CONTEXT.get();
    }

    public static TenantContext getRequiredContext() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new BusinessException("TENANT_NOT_RESOLVED", "No authenticated tenant context found for the current execution thread.");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
