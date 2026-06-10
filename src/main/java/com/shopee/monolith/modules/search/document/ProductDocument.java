package com.shopee.monolith.modules.search.document;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Elasticsearch document for product keyword/facet search.
 * Contains only public catalog fields — no PII or internal state.
 * Indexed after AFTER_COMMIT via ProductCatalogEventListener.
 */
@Document(indexName = "products", createIndex = false)
@Getter
@Builder
public class ProductDocument {

    @Id
    private String productId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String categoryPath;

    @Field(type = FieldType.Keyword)
    private String brand;

    /** Flattened attribute map for facet search. */
    @Field(type = FieldType.Object, enabled = false)
    private Map<String, Object> attributes;

    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    @Field(type = FieldType.Keyword)
    private String shopId;

    @Field(type = FieldType.Keyword)
    private String shopName;

    @Field(type = FieldType.Double)
    private BigDecimal shopRating;

    @Field(type = FieldType.Keyword)
    private String coverImageUrl;

    @Field(type = FieldType.Keyword)
    private String coverMediaId;

    /** Document status — only ACTIVE documents are returned in public search. */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    public static ProductDocument fromUUID(UUID id) {
        return ProductDocument.builder().productId(id.toString()).build();
    }
}
