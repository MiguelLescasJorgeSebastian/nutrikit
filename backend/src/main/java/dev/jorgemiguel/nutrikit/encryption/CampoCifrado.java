package dev.jorgemiguel.nutrikit.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.ObjectProvider;

import dev.jorgemiguel.nutrikit.encryption.infrastructure.CifradoDeCampos;

/**
 * Convertidor JPA para texto sensible: lo cifra al guardar y lo descifra al leer, con la llave del
 * consultorio de la petición. Uso, sobre una columna {@code bytea}:
 *
 * <pre>{@code
 * @Convert(converter = CampoCifrado.class)
 * private String alergias;
 * }</pre>
 *
 * <p>Hibernate lo crea a través de Spring. Recibe el servicio con {@link ObjectProvider} para
 * resolverlo solo al usarse: así las pruebas parciales de JPA, que no cargan este módulo, no fallan
 * al arrancar.
 */
@Converter
public class CampoCifrado implements AttributeConverter<String, byte[]> {

	private final ObjectProvider<CifradoDeCampos> cifrado;

	public CampoCifrado(ObjectProvider<CifradoDeCampos> cifrado) {
		this.cifrado = cifrado;
	}

	@Override
	public byte[] convertToDatabaseColumn(String texto) {
		return cifrado.getObject().cifrar(texto);
	}

	@Override
	public String convertToEntityAttribute(byte[] dato) {
		return cifrado.getObject().descifrar(dato);
	}

}
