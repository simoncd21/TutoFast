package com.tutofast.config;

import java.math.BigDecimal;
import java.util.List;

import org.bson.types.Decimal128;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Guarda los {@code BigDecimal} (dinero en COP y porcentajes) como {@code Decimal128}, no como texto: así Atlas
 * puede ordenar, comparar y filtrar por precio. La lectura sigue aceptando valores antiguos guardados como texto.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new BigDecimalADecimal128(), new Decimal128ABigDecimal()));
    }

    @WritingConverter
    static class BigDecimalADecimal128 implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(BigDecimal origen) {
            return new Decimal128(origen);
        }
    }

    @ReadingConverter
    static class Decimal128ABigDecimal implements Converter<Decimal128, BigDecimal> {
        @Override
        public BigDecimal convert(Decimal128 origen) {
            return origen.bigDecimalValue();
        }
    }
}
