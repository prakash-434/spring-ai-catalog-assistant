package com.kaarislabs.chatbot.repository;

import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.kaarislabs.chatbot.repository.row.BatchDocumentsRow;

/**
 * Batch document lookups on OIBT. Only an exact item code + batch number pair is ever looked up; there is deliberately
 * no method that lists the batches of a product.
 */
@Repository
public class BatchRepository {

    private static final RowMapper<BatchDocumentsRow> DOCS = (rs, i) -> new BatchDocumentsRow(
            rs.getString("ItemCode"),
            rs.getString("BatchNum"),
            rs.getBoolean("HasCoa"),
            rs.getBoolean("HasHplc"),
            rs.getBoolean("HasMass"),
            rs.getBoolean("HasNmr"),
            rs.getBoolean("Has13CNmr"),
            rs.getBoolean("HasIr"),
            rs.getBoolean("HasTga"));

    private final NamedParameterJdbcTemplate jdbc;

    public BatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ported from CoaRepository.findByItemCodeAndBatch(). The document columns hold SAP attachment paths; the SQL turns
     * each into a has-document flag so the paths are never read.
     */
    public Optional<BatchDocumentsRow> findByItemCodeAndBatch(String itemCode, String batchNum) {
        String sql = """
                SELECT TOP 1
                  T0.ItemCode AS ItemCode,
                  T0.BatchNum AS BatchNum,
                  %s AS HasCoa,
                  %s AS HasHplc,
                  %s AS HasMass,
                  %s AS HasNmr,
                  %s AS Has13CNmr,
                  %s AS HasIr,
                  %s AS HasTga
                FROM OIBT T0
                INNER JOIN OITM T1 ON T1.ItemCode = T0.ItemCode
                WHERE T0.ItemCode = :itemCode
                  AND T0.BatchNum = :batchNum
                """.formatted(
                hasValue("T0.U_COA"), hasValue("T0.U_HPLC"), hasValue("T0.U_MASS"), hasValue("T0.U_NMR"),
                hasValue("T0.U_13CNMR"), hasValue("T0.U_IR"), hasValue("T0.U_TGA"));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("itemCode", itemCode)
                .addValue("batchNum", batchNum);
        return jdbc.query(sql, params, DOCS).stream().findFirst();
    }

    /** 1 when the (possibly ntext) column holds a non-blank value, else 0. Column names are constants, never input. */
    private static String hasValue(String column) {
        return "CAST(CASE WHEN NULLIF(LTRIM(RTRIM(CAST(" + column
                + " AS nvarchar(max)))), '') IS NULL THEN 0 ELSE 1 END AS bit)";
    }
}
