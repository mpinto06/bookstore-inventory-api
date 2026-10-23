-- Los timestamps los mantiene la BD. TIMESTAMPTZ guarda siempre el instante en UTC; la zona de sesión
-- solo afecta cómo se muestra, por eso la app fuerza UTC en la conexión (hibernate.jdbc.time_zone).
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.created_at := OLD.created_at;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_books_set_updated_at
    BEFORE UPDATE ON books
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
