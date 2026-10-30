package com.kaarislabs.chatbot.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.row.Page;
import com.kaarislabs.chatbot.repository.row.ProductRow;

/**
 * Product queries against OITM/OITB. Every query is parameterised, capped with TOP and limited to
 * {@code U_WebsiteDisplay = 'YES'}. Inputs are expected to be validated by the calling service.
 */
@Repository
public class ProductRepository {

    private static final String VISIBLE = "I.U_WebsiteDisplay = 'YES'";

    private static final String FROM = "FROM OITM I\nINNER JOIN OITB B ON I.ItmsGrpCod = B.ItmsGrpCod\n";

    private static final String SUMMARY_COLUMNS = """
            I.ItemCode            AS ItemCode,
            I.ItemName            AS ItemName,
            B.ItmsGrpNam          AS ItmsGrpNam,
            I.U_APIFamily         AS U_APIFamily,
            I.U_CasNo             AS U_CasNo,
            I.U_MolecularFormula  AS U_MolecularFormula,
            I.U_MolecularWeight   AS U_MolecularWeight,
            I.U_InventoryStatus   AS U_InventoryStatus,
            I.U_Brand             AS U_Brand
            """;

    private static final String DETAIL_COLUMNS = """
            I.ItemCode             AS ItemCode,
            I.ItemName             AS ItemName,
            B.ItmsGrpNam           AS ItmsGrpNam,
            I.U_APIFamily          AS U_APIFamily,
            I.U_CasNo              AS U_CasNo,
            I.U_AltCasNo           AS U_AltCasNo,
            I.U_MolecularFormula   AS U_MolecularFormula,
            I.U_MolecularWeight    AS U_MolecularWeight,
            I.U_InventoryStatus    AS U_InventoryStatus,
            I.U_IUPACName          AS U_IUPACName,
            I.U_Synonyms           AS U_Synonyms,
            I.U_Smiles             AS U_Smiles,
            I.U_Solvent            AS U_Solvent,
            I.U_StableIsotopesType AS U_StableIsotopesType,
            I.U_StorageTemp        AS U_StorageTemp,
            I.U_ProductFormat      AS U_ProductFormat,
            I.U_PurityByHPLC       AS U_PurityByHPLC,
            I.U_ShippingTemp       AS U_ShippingTemp,
            I.U_Brand              AS U_Brand
            """;

    private static final RowMapper<ProductRow> SUMMARY_MAPPER = (rs, i) -> new ProductRow(
            rs.getString("ItemCode"), rs.getString("ItemName"), rs.getString("ItmsGrpNam"),
            rs.getString("U_APIFamily"), rs.getString("U_CasNo"), null,
            rs.getString("U_MolecularFormula"), rs.getString("U_MolecularWeight"), rs.getString("U_InventoryStatus"),
            null, null, null, null, null, null, null, null, null,
            rs.getString("U_Brand"));

    private static final RowMapper<ProductRow> DETAIL_MAPPER = (rs, i) -> new ProductRow(
            rs.getString("ItemCode"), rs.getString("ItemName"), rs.getString("ItmsGrpNam"),
            rs.getString("U_APIFamily"), rs.getString("U_CasNo"), rs.getString("U_AltCasNo"),
            rs.getString("U_MolecularFormula"), rs.getString("U_MolecularWeight"), rs.getString("U_InventoryStatus"),
            rs.getString("U_IUPACName"), rs.getString("U_Synonyms"), rs.getString("U_Smiles"),
            rs.getString("U_Solvent"), rs.getString("U_StableIsotopesType"), rs.getString("U_StorageTemp"),
            rs.getString("U_ProductFormat"), rs.getString("U_PurityByHPLC"), rs.getString("U_ShippingTemp"),
            rs.getString("U_Brand"));

    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogueProperties catalogue;

    public ProductRepository(NamedParameterJdbcTemplate jdbc, CatalogueProperties catalogue) {
        this.jdbc = jdbc;
        this.catalogue = catalogue;
    }

    /**
     * Free-text search, ported from SearchListRepository.searchProducts(): LIKE across item code, CAS, alt CAS, name,
     * category, IUPAC name and synonyms; prefix stripping; multi-token AND; same ranking.
     *
     * @param category optional exact category name; null searches every category except the non-product ones
     */
    public Page<ProductRow> search(String query, String category, int limit) {
        SearchTerms terms = SearchTerms.of(query);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = searchWhere(terms, category, params);

        params.addValue("exact", terms.trimmed());
        params.addValue("limit", limit);

        String sql = "SELECT TOP (:limit) " + SUMMARY_COLUMNS + FROM + "WHERE " + where + """
                ORDER BY
                  CASE
                    WHEN I.ItemCode = :exact THEN 1
                    WHEN ISNULL(I.U_CasNo, '') = :exact THEN 2
                    WHEN ISNULL(I.U_AltCasNo, '') = :exact THEN 3
                    WHEN I.ItemCode LIKE :term1 ESCAPE '\\' THEN 4
                    WHEN ISNULL(I.U_CasNo, '') LIKE :term1 ESCAPE '\\' THEN 5
                    WHEN ISNULL(I.U_AltCasNo, '') LIKE :term1 ESCAPE '\\' THEN 6
                    WHEN ISNULL(I.U_IUPACName, '') LIKE :term1 ESCAPE '\\' THEN 7
                    WHEN ISNULL(I.U_Synonyms, '') LIKE :term1 ESCAPE '\\' THEN 8
                    ELSE 9
                  END,
                  I.ItemCode
                """;

        List<ProductRow> items = jdbc.query(sql, params, SUMMARY_MAPPER);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + "WHERE " + where, params, Long.class);
        return new Page<>(total == null ? 0 : total, items);
    }

    /**
     * Product by catalogue number, ported from ProductDetailRepository.findByCatNo(): exact ItemCode first, then the
     * first ItemCode starting with {@code catNo + '-'}. Non-product categories are never returned.
     */
    public Optional<ProductRow> findByCatNo(String catNo) {
        MapSqlParameterSource params = new MapSqlParameterSource("catNo", catNo);
        List<String> conditions = new ArrayList<>(List.of(VISIBLE));
        addNotIn(conditions, params, "nonProduct", catalogue.nonProductCategories());
        String filters = String.join(" AND ", conditions);
        String base = "SELECT TOP 1 " + DETAIL_COLUMNS + FROM;

        List<ProductRow> exact = jdbc.query(base + "WHERE I.ItemCode = :catNo AND " + filters, params, DETAIL_MAPPER);
        if (!exact.isEmpty()) {
            return Optional.of(exact.getFirst());
        }
        List<ProductRow> prefixed = jdbc.query(base
                + "WHERE (I.ItemCode = :catNo OR I.ItemCode LIKE :catNo + '-%') AND " + filters
                + " ORDER BY I.ItemCode ASC", params, DETAIL_MAPPER);
        return prefixed.stream().findFirst();
    }

    /**
     * Products in a category / API family / stock status, ported from CategoryPageRepository.findWithFilters() and
     * countWithFilters() (same ORDER BY ItemName). Null filters are not applied.
     */
    public Page<ProductRow> browse(String category, String apiFamily, StockStatusFilter stockStatus, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>(List.of(VISIBLE));
        addNotIn(conditions, params, "excluded", catalogue.excludedCategories());
        if (category != null) {
            conditions.add("B.ItmsGrpNam = :category");
            params.addValue("category", category);
        }
        if (apiFamily != null) {
            conditions.add("I.U_APIFamily = :apiFamily");
            params.addValue("apiFamily", apiFamily);
        }
        if (stockStatus != null) {
            conditions.add("I.U_InventoryStatus = :stockStatus");
            params.addValue("stockStatus", stockStatus.label());
        }
        String where = String.join("\n  AND ", conditions) + "\n";
        params.addValue("limit", limit);

        List<ProductRow> items = jdbc.query(
                "SELECT TOP (:limit) " + SUMMARY_COLUMNS + FROM + "WHERE " + where + "ORDER BY I.ItemName ASC",
                params, SUMMARY_MAPPER);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + "WHERE " + where, params, Long.class);
        return new Page<>(total == null ? 0 : total, items);
    }

    private String searchWhere(SearchTerms terms, String category, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>(List.of(VISIBLE));
        addNotIn(conditions, params, "nonProduct", catalogue.nonProductCategories());
        if (category != null) {
            conditions.add("B.ItmsGrpNam = :category");
            params.addValue("category", category);
        }

        params.addValue("term1", terms.term1());
        StringBuilder match = new StringBuilder("(").append(fieldMatch("term1"));
        if (terms.term2() != null) {
            params.addValue("term2", terms.term2());
            match.append("\n    OR ").append(fieldMatch("term2"));
        }
        if (!terms.tokens().isEmpty()) {
            List<String> perToken = new ArrayList<>();
            for (int i = 0; i < terms.tokens().size(); i++) {
                String name = "token" + i;
                params.addValue(name, terms.tokens().get(i));
                perToken.add("(" + fieldMatch(name) + ")");
            }
            match.append("\n    OR (").append(String.join(" AND ", perToken)).append(")");
        }
        conditions.add(match.append(")").toString());
        return String.join("\n  AND ", conditions) + "\n";
    }

    /** One search term against every searchable field (OR across fields). */
    private static String fieldMatch(String param) {
        String like = " LIKE :" + param + " ESCAPE '\\'";
        return "I.ItemCode" + like
                + " OR ISNULL(I.U_CasNo, '')" + like
                + " OR ISNULL(I.U_AltCasNo, '')" + like
                + " OR I.ItemName" + like
                + " OR B.ItmsGrpNam" + like
                + " OR ISNULL(I.U_IUPACName, '')" + like
                + " OR ISNULL(I.U_Synonyms, '')" + like;
    }

    /** Adds {@code B.ItmsGrpNam NOT IN (:param)} unless the category list is empty. */
    private static void addNotIn(List<String> conditions, MapSqlParameterSource params, String param,
            List<String> categories) {
        if (!categories.isEmpty()) {
            conditions.add("B.ItmsGrpNam NOT IN (:" + param + ")");
            params.addValue(param, categories);
        }
    }
}
