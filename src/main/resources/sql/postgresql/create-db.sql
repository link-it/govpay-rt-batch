-- PostgreSQL DDL for RT_TEMP table (Ricevute Telematiche)

CREATE TABLE RT_TEMP (
    id                  BIGSERIAL PRIMARY KEY,
    id_rendicontazione  BIGINT NOT NULL,
    cod_dominio         VARCHAR(35) NOT NULL,
    iuv                 VARCHAR(35) NOT NULL,
    iur                 VARCHAR(35) NOT NULL
);

-- Create indexes for common queries
CREATE INDEX idx_rt_temp_id_rendicontazione ON RT_TEMP(id_rendicontazione);
CREATE INDEX idx_rt_temp_cod_dominio ON RT_TEMP(cod_dominio);
CREATE INDEX idx_rt_temp_iuv ON RT_TEMP(iuv);
CREATE INDEX idx_rt_temp_iur ON RT_TEMP(iur);

-- Optimized composite index for batch processing queries
CREATE INDEX idx_rt_temp_dominio_iuv ON RT_TEMP(cod_dominio, iuv);

-- Add table and column comments
COMMENT ON TABLE RT_TEMP IS 'Temporary table for storing RT (Ricevute Telematiche) during batch processing';
COMMENT ON COLUMN RT_TEMP.id IS 'Primary key';
COMMENT ON COLUMN RT_TEMP.id_rendicontazione IS 'Rendicontazione identifier';
COMMENT ON COLUMN RT_TEMP.cod_dominio IS 'Domain code';
COMMENT ON COLUMN RT_TEMP.iuv IS 'Identificativo Univoco Versamento';
COMMENT ON COLUMN RT_TEMP.iur IS 'Identificativo Univoco Riscossione';
