-- V12__statutory_business_sectors.sql
-- Directive No. 1142/2026 Art. 4(4)(c) & Annex 2
-- Official Statutory Business Sector Master and Taxpayer Sector Classification

CREATE TABLE IF NOT EXISTS business_sectors (
    sector_code VARCHAR(32) PRIMARY KEY,
    name_en VARCHAR(255) NOT NULL,
    name_am VARCHAR(255) NOT NULL,
    is_mandatory_offline_continuity BOOLEAN NOT NULL DEFAULT FALSE,
    source_reference VARCHAR(64) NOT NULL DEFAULT 'DIRECTIVE_1142_ANNEX_2',
    effective_version VARCHAR(32) NOT NULL DEFAULT '2018_EC_2026_GC',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_business_sectors_offline ON business_sectors(is_mandatory_offline_continuity);

-- Link taxpayer profiles with statutory business sector
ALTER TABLE taxpayer_profiles
    ADD COLUMN IF NOT EXISTS sector_code VARCHAR(32) REFERENCES business_sectors(sector_code),
    ADD COLUMN IF NOT EXISTS mandatory_offline_continuity BOOLEAN DEFAULT FALSE;

-- Seed the 26 mandatory offline continuity sectors from Annex 2 of Directive 1142/2026
INSERT INTO business_sectors (sector_code, name_en, name_am, is_mandatory_offline_continuity) VALUES
('SEC-01', 'Retail sale of food', 'የምግብ ችርቻሮ ንግድ', TRUE),
('SEC-02', 'Retail sale of beverages', 'የመጠጥ ችርቻሮ ንግድ', TRUE),
('SEC-03', 'Retail sale of tobacco products', 'ሲጋራና የሲጋራ ውጤቶች ችርቻሮ ንግድ', TRUE),
('SEC-04', 'Retail sale of automotive fuel', 'የነዳጅና ነዳጅ ውጤቶች ችርቻሮ ንግድ', TRUE),
('SEC-05', 'Retail sale of textiles', 'የጨርቃጨርቅ ችርቻሮ ንግድ', TRUE),
('SEC-06', 'Retail sale of books, newspapers, stationery and office supplies', 'የመጸሃፍት፣ ጋዜጦች፣ የጽህፈት መሳሪያዎችና የቢሮ እቃዎች ችርቻሮ ንግድ', TRUE),
('SEC-07', 'Retail sale of clothing, footwear and leather articles', 'ልብስ፣ ጨርቃጨርቅና የቆዳ ውጤቶች ችርቻሮ ንግድ', TRUE),
('SEC-08', 'Retail sale of pharmaceutical and medical goods, cosmetic and toilet articles', 'መድሃኒቶች፣ የህክምና መሳሪያዎች፣ የውበትና ንፅህና መጠበቂያ ምርቶች ችርቻሮ ንግድ', TRUE),
('SEC-09', 'Retail sale of other new goods', 'ሌሎች የአዲስ እቃዎች ችርቻሮ ንግድ', TRUE),
('SEC-10', 'Retail sale of second-hand goods', 'ሌሎች ያገለግሉ እቃዎች ችርቻሮ ንግድ', TRUE),
('SEC-11', 'Retail sale of motor vehicles, motorcycles and related parts and accessories', 'የተሽከርካሪዎችና ባለሞተር ብስክሌቶች መለዋወጫ የችርቻሮ ንግድ', TRUE),
('SEC-12', 'Passenger rail transport, interurban', 'የከተማ ለከተማ ባቡር ትራንስፓርት', TRUE),
('SEC-13', 'Urban and suburban passenger land transport', 'የከተማና የንዑስ ከተማ የመንገደኞች ማጓጓዣ', TRUE),
('SEC-14', 'Other passenger land transport', 'ሌሎች የመንገደኞች ማጓጓዣ', TRUE),
('SEC-15', 'Postal activities', 'የመልዕክት አገልግሎት', TRUE),
('SEC-16', 'Short term accommodation activities', 'የአጭር ጊዜ ማረፊያ ቦታዎች ሥራዎች', TRUE),
('SEC-17', 'Camping grounds, recreational vehicle parks and trailer parks', 'ውጭ መዝናኛና ሽርሽር ተሽካርካሪዎችና መኪና ማቆሚያ ሌሎች አገልግሎቶች', TRUE),
('SEC-18', 'Restaurants and mobile food service activities', 'ምግብ ቤቶችና ተንቀሳቃሽ የምግብ አገልግሎቶች', TRUE),
('SEC-19', 'Beverage serving activities', 'የመጠጥ አገልግሎቶች', TRUE),
('SEC-20', 'Hospital activities', 'የህክምና ተቋም አገልግሎቶች', TRUE),
('SEC-21', 'Medical and dental practice activities', 'የህክምና እና የጥርስ አገልግሎት', TRUE),
('SEC-22', 'Hairdressing and barber activities', 'የጸጉር ውበትና ጸጉር ማስተካክል ስራዎች', TRUE),
('SEC-23', 'Beauty care and other beauty treatment activities', 'የውበት መጠበቂያና ሌሎች የውበት እንክብካቤ ስራዎች', TRUE),
('SEC-24', 'Day spa, sauna and steam bath activities', 'የፍል ውሃ፣ ሳውና እና እጥበት አገልግሎቶች', TRUE),
('SEC-25', 'Veterinary activities', 'የእንሣት ህክምና', TRUE),
('SEC-26', 'Activities of amusement parks and theme parks', 'የመዝናኛ ቦታዎች', TRUE)
ON CONFLICT (sector_code) DO NOTHING;

-- Seed general non-mandatory sectors
INSERT INTO business_sectors (sector_code, name_en, name_am, is_mandatory_offline_continuity) VALUES
('SEC-GEN-WHOLESALE', 'Wholesale Trade & Distribution', 'የጅምላ ንግድና ስርጭት', FALSE),
('SEC-GEN-MANUFACTURING', 'Manufacturing & Industrial Production', 'ማምረቻና ኢንዱስትሪ', FALSE),
('SEC-GEN-CONSTRUCTION', 'Construction & Engineering Works', 'ግንባታና ምህንድስና', FALSE),
('SEC-GEN-IT-SERVICES', 'Information Technology & Consulting Services', 'የኢንፎርሜሽን ቴክኖሎጂና አማካሪ አገልግሎቶች', FALSE),
('SEC-GEN-AGRICULTURE', 'Agriculture & Farming', 'ግብርናና እርሻ', FALSE)
ON CONFLICT (sector_code) DO NOTHING;
