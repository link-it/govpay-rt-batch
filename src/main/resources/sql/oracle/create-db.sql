-- Oracle DDL for RT_TEMP table (Ricevute Telematiche)
-- Auto-increment implemented via SEQUENCE and TRIGGER

-- Create sequence for primary key
CREATE SEQUENCE RT_TEMP_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create table
CREATE TABLE RT_TEMP (
    id                  NUMBER PRIMARY KEY,
    id_rendicontazione  NUMBER NOT NULL,
    cod_dominio         VARCHAR2(35 CHAR) NOT NULL,
    iuv                 VARCHAR2(35 CHAR) NOT NULL,
    iur                 VARCHAR2(35 CHAR) NOT NULL
);

-- Create trigger for auto-increment
CREATE OR REPLACE TRIGGER RT_TEMP_TRG
BEFORE INSERT ON RT_TEMP
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT RT_TEMP_SEQ.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
END;
/

-- Create indexes for common queries
CREATE INDEX idx_rt_temp_id_rendicontazione ON RT_TEMP(id_rendicontazione);
CREATE INDEX idx_rt_temp_cod_dominio ON RT_TEMP(cod_dominio);
CREATE INDEX idx_rt_temp_iuv ON RT_TEMP(iuv);
CREATE INDEX idx_rt_temp_iur ON RT_TEMP(iur);

-- Optimized composite index for batch processing queries
CREATE INDEX idx_rt_temp_dominio_iuv ON RT_TEMP(cod_dominio, iuv);

-- Add comments
COMMENT ON TABLE RT_TEMP IS 'Temporary table for storing RT (Ricevute Telematiche) during batch processing';
COMMENT ON COLUMN RT_TEMP.id IS 'Primary key';
COMMENT ON COLUMN RT_TEMP.id_rendicontazione IS 'Rendicontazione identifier';
COMMENT ON COLUMN RT_TEMP.cod_dominio IS 'Domain code';
COMMENT ON COLUMN RT_TEMP.iuv IS 'Identificativo Univoco Versamento';
COMMENT ON COLUMN RT_TEMP.iur IS 'Identificativo Univoco Riscossione';
