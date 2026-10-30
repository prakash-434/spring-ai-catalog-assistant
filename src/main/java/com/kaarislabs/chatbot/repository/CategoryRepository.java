package com.kaarislabs.chatbot.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.row.NameCount;
import com.kaarislabs.chatbot.repository.row.Page;

/** Category (OITB) and API family (OITM.U_APIFamily) queries. */
@Repository
public class CategoryRepository {

    private static final int MAX_CATEGORIES = 200;

    private static final RowMapper<NameCount> NAME_COUNT = (rs, i) ->
            new NameCount(rs.getString("Name"), rs.getLong("ProductCount"));

    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogueProperties catalogue;

    public CategoryRepository(NamedParameterJdbcTemplate jdbc, CatalogueProperties catalogue) {
        this.jdbc = jdbc;
        this.catalogue = catalogue;
    }

    /**
     * All categories except the configured exclusions, each with its number of website-visible products. Combines
     * CategoryRepository.findAll() and CategoryPageRepository.getProductCountByCategory() from the website.
     */
    public List<NameCount> findAllWithCounts() {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", MAX_CATEGORIES);
        String exclusion = "";
        if (!catalogue.excludedCategories().isEmpty()) {
            exclusion = "WHERE B.ItmsGrpNam NOT IN (:excluded)\n";
            params.addValue("excluded", catalogue.excludedCategories());
        }
        String sql = "SELECT TOP (:limit) B.ItmsGrpNam AS Name, COUNT(I.ItemCode) AS ProductCount\n"
                + "FROM OITB B\n"
                + "LEFT JOIN OITM I ON I.ItmsGrpCod = B.ItmsGrpCod AND I.U_WebsiteDisplay = 'YES'\n"
                + exclusion
                + "GROUP BY B.ItmsGrpNam\n"
                + "ORDER BY B.ItmsGrpNam";
        return jdbc.query(sql, params, NAME_COUNT);
    }

    /**
     * API families with product counts, ported from CategoryPageRepository.getProductCountByAPIFamily(). Ordered by
     * product count (largest first).
     *
     * @param category optional exact category name; null covers all non-excluded categories
     * @param query    optional text the family name must contain
     */
    public Page<NameCount> findApiFamilies(String category, String query, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>(List.of(
                "I.U_WebsiteDisplay = 'YES'",
                "I.U_APIFamily IS NOT NULL",
                "I.U_APIFamily != ''"));
        if (!catalogue.excludedCategories().isEmpty()) {
            conditions.add("B.ItmsGrpNam NOT IN (:excluded)");
            params.addValue("excluded", catalogue.excludedCategories());
        }
        if (category != null) {
            conditions.add("B.ItmsGrpNam = :category");
            params.addValue("category", category);
        }
        if (query != null) {
            conditions.add("I.U_APIFamily LIKE :query ESCAPE '\\'");
            params.addValue("query", "%" + SearchTerms.escapeLike(query) + "%");
        }
        String from = "FROM OITM I\nINNER JOIN OITB B ON I.ItmsGrpCod = B.ItmsGrpCod\nWHERE "
                + String.join("\n  AND ", conditions) + "\n";
        params.addValue("limit", limit);

        List<NameCount> families = jdbc.query(
                "SELECT TOP (:limit) I.U_APIFamily AS Name, COUNT(*) AS ProductCount\n" + from
                        + "GROUP BY I.U_APIFamily\nORDER BY COUNT(*) DESC, I.U_APIFamily ASC",
                params, NAME_COUNT);
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT I.U_APIFamily)\n" + from, params, Long.class);
        return new Page<>(total == null ? 0 : total, families);
    }
}
