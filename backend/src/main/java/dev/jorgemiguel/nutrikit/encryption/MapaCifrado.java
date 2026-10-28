package dev.jorgemiguel.nutrikit.encryption;

import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.ObjectProvider;

import dev.jorgemiguel.nutrikit.encryption.infrastructure.CifradoDeCampos;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convertidor JPA para un mapa de texto sensible (campos adicionales que define cada consultorio):
 * se serializa a JSON y el JSON se cifra. En la base es {@code bytea}, no {@code jsonb}: cifrado
 * ya no se puede consultar como JSON.
 */
@Converter
public class MapaCifrado implements AttributeConverter<Map<String, String>, byte[]> {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final TypeReference<TreeMap<String, String>> TIPO = new TypeReference<>() {
	};

	private final ObjectProvider<CifradoDeCampos> cifrado;

	public MapaCifrado(ObjectProvider<CifradoDeCampos> cifrado) {
		this.cifrado = cifrado;
	}

	@Override
	public byte[] convertToDatabaseColumn(Map<String, String> mapa) {
		if (mapa == null || mapa.isEmpty()) {
			return null;
		}
		return cifrado.getObject().cifrar(JSON.writeValueAsString(new TreeMap<>(mapa)));
	}

	@Override
	public Map<String, String> convertToEntityAttribute(byte[] dato) {
		String json = cifrado.getObject().descifrar(dato);
		return json == null ? Map.of() : Map.copyOf(JSON.readValue(json, TIPO));
	}

}
