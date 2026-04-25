package com.cinetrack.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that transparently converts {@link EmailAddress}
 * to/from a plain {@code VARCHAR} column.
 *
 * <h2>How {@code AttributeConverter} fits into Hibernate 7</h2>
 * The converter participates in the {@code JavaType}/{@code JdbcType} pipeline:
 * <ol>
 *   <li>On write: Hibernate calls {@link #convertToDatabaseColumn} to get a
 *       {@code String}, then uses the {@code VarcharJdbcType} binder to set
 *       the JDBC parameter.</li>
 *   <li>On read: Hibernate reads the column as a {@code String} via
 *       {@code VarcharJdbcType}, then calls {@link #convertToEntityAttribute}
 *       to reconstruct the {@code EmailAddress}.</li>
 * </ol>
 *
 * <h2>{@code autoApply = true}</h2>
 * With auto-apply enabled, Hibernate registers this converter globally.  Any
 * entity field of type {@code EmailAddress} — present or future — is converted
 * automatically without needing a per-field {@code @Convert} annotation.
 * Opt out on a specific field with {@code @Convert(disableConversion=true)}.
 */
@Converter(autoApply = true)
public class EmailAddressConverter implements AttributeConverter<EmailAddress, String> {

    /**
     * Called before INSERT / UPDATE — converts the domain object to a DB value.
     *
     * @return the normalised e-mail string, or {@code null} if {@code attribute} is null
     */
    @Override
    public String convertToDatabaseColumn(EmailAddress attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    /**
     * Called after SELECT — reconstructs the domain object from the stored string.
     * Validation runs inside the {@link EmailAddress} constructor, so a corrupt
     * DB value will throw at load time rather than silently producing garbage.
     *
     * @return a validated {@link EmailAddress}, or {@code null} if {@code dbData} is null
     */
    @Override
    public EmailAddress convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new EmailAddress(dbData);
    }
}
