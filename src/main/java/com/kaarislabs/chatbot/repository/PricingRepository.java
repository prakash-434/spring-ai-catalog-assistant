package com.kaarislabs.chatbot.repository;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.kaarislabs.chatbot.config.CatalogueProperties;
import com.kaarislabs.chatbot.repository.row.PackRow;

/**
 * Pack sizes and raw prices from [@PRICING_H]/[@PRICING_R]. Callers must go through PricingService, which decides what
 * may be shown.
 */
@Repository
public class PricingRepository {

    private static final int MAX_PACK_ROWS = 50;

    private static final RowMapper<PackRow> PACK = (rs, i) -> new PackRow(
            rs.getString("ItemCode"),
            rs.getBigDecimal("U_Quantity"),
            rs.getString("U_UOM"),
            rs.getBigDecimal("U_Price"),
            rs.getBigDecimal("U_PriceUSD"),
            rs.getString("U_IndiaPriceDisplay"));

    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogueProperties catalogue;

    public PricingRepository(NamedParameterJdbcTemplate jdbc, CatalogueProperties catalogue) {
        this.jdbc = jdbc;
        this.catalogue = catalogue;
    }

    /**
     * Pack rows for a catalogue number, combining ProductDetailRepository.findPackSizesByBaseCatNo() (item match and
     * filters) with PricingRepository.getPricingByCatNo() (price columns) from the website. Warehouse stock (OITW) is
     * deliberately not read. Non-product categories are never priced.
     */
    public List<PackRow> findPackRows(String catNo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("catNo", catNo)
                .addValue("limit", MAX_PACK_ROWS);
        String nonProduct = "";
        if (!catalogue.nonProductCategories().isEmpty()) {
            nonProduct = "  AND B.ItmsGrpNam NOT IN (:nonProduct)\n";
            params.addValue("nonProduct", catalogue.nonProductCategories());
        }
        String sql = """
                SELECT TOP (:limit)
                  T0.ItemCode            AS ItemCode,
                  T0.U_IndiaPriceDisplay AS U_IndiaPriceDisplay,
                  T4.U_Quantity          AS U_Quantity,
                  T4.U_UOM               AS U_UOM,
                  T4.U_Price             AS U_Price,
                  T4.U_PriceUSD          AS U_PriceUSD
                FROM OITM T0
                INNER JOIN OITB B ON T0.ItmsGrpCod = B.ItmsGrpCod
                INNER JOIN [@PRICING_H] T3 ON T0.ItemCode = T3.U_Code
                INNER JOIN [@PRICING_R] T4 ON T3.DocEntry = T4.DocEntry
                WHERE (T0.ItemCode = :catNo OR T0.ItemCode LIKE :catNo + '-%')
                  AND T0.U_WebsiteDisplay = 'YES'
                  AND T4.U_UOM IS NOT NULL
                  AND T4.U_Quantity IS NOT NULL
                  AND T4.U_Quantity > 0
                """ + nonProduct + "ORDER BY T4.U_Quantity ASC, T0.ItemCode ASC";
        return jdbc.query(sql, params, PACK);
    }
}
