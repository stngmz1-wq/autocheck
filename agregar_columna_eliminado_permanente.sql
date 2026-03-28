-- Script para agregar la columna eliminado_permanente a la tabla mensaje
-- Ejecutar este script en MySQL para habilitar la funcionalidad de "Vaciar Papelera"

USE auto;

-- Agregar columna eliminado_permanente después de la columna eliminado
ALTER TABLE mensaje 
ADD COLUMN eliminado_permanente BOOLEAN DEFAULT FALSE AFTER eliminado;

-- Verificar que la columna se agregó correctamente
DESCRIBE mensaje;

-- Opcional: Ver los primeros registros para confirmar
SELECT id, asunto, eliminado, eliminado_permanente FROM mensaje LIMIT 5;
