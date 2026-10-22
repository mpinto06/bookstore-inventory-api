CREATE TABLE books (
    id                  BIGSERIAL      PRIMARY KEY,
    title               VARCHAR(255)   NOT NULL,
    author              VARCHAR(255)   NOT NULL,
    isbn                VARCHAR(13)    NOT NULL,
    cost_usd            NUMERIC(10, 2) NOT NULL,
    selling_price_local NUMERIC(14, 2),
    selling_currency    CHAR(3),
    stock_quantity      INTEGER        NOT NULL,
    category            VARCHAR(100)   NOT NULL,
    supplier_country    CHAR(2)        NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT chk_books_cost_usd_positive CHECK (cost_usd > 0),
    CONSTRAINT chk_books_stock_non_negative CHECK (stock_quantity >= 0),
    CONSTRAINT chk_books_price_with_currency CHECK (
        (selling_price_local IS NULL AND selling_currency IS NULL)
        OR (selling_price_local IS NOT NULL AND selling_currency IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_books_isbn ON books (isbn);
CREATE INDEX idx_books_category ON books (category);
CREATE INDEX idx_books_stock_quantity ON books (stock_quantity);
