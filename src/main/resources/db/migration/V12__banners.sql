-- =====================================================================
-- Banner หน้าร้าน (สไลด์บนสุด / ข้อเสนอพิเศษกลางหน้า) — แอดมินสร้างเองทุกใบ
-- =====================================================================

CREATE TABLE banner (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    placement         VARCHAR(20)   NOT NULL,
    image_url         VARCHAR(500)  NOT NULL,
    image_url_mobile  VARCHAR(500),
    title             VARCHAR(150),
    title_en          VARCHAR(150),
    subtitle          VARCHAR(300),
    subtitle_en       VARCHAR(300),
    cta_label         VARCHAR(50),
    cta_label_en      VARCHAR(50),
    link_url          VARCHAR(500),
    promotion_id      BIGINT        REFERENCES promotion (id) ON DELETE SET NULL,
    sort_order        INT           NOT NULL DEFAULT 0,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    start_at          TIMESTAMPTZ,
    end_at            TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(50),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_banner_placement CHECK (placement IN ('HOME_HERO', 'HOME_OFFER')),
    CONSTRAINT ck_banner_period    CHECK (start_at IS NULL OR end_at IS NULL OR end_at > start_at)
);

CREATE INDEX ix_banner_placement ON banner (placement, sort_order, id);
