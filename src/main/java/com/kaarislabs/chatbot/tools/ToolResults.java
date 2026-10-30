package com.kaarislabs.chatbot.tools;

import java.util.List;

import com.kaarislabs.chatbot.service.dto.ProductDetail;
import com.kaarislabs.chatbot.service.dto.ProductSummary;

/**
 * What the model sees. Mirrors the service DTOs without links (buttons go into actions) and without brand (the
 * website's product page does not show it).
 */
public final class ToolResults {

    private ToolResults() {
    }

    public record ProductHit(String catNo, String name, String category, String apiFamily, String casNo,
            String molecularFormula, String molecularWeight, String stockStatus) {

        static ProductHit of(ProductSummary p) {
            return new ProductHit(p.catNo(), p.name(), p.category(), p.apiFamily(), p.casNo(), p.molecularFormula(),
                    p.molecularWeight(), p.stockStatus());
        }
    }

    public record ProductList(long total, List<ProductHit> items) {
    }

    public record ProductInfo(boolean found, String catNo, String name, String category, String apiFamily,
            String casNo, String altCasNo, String molecularFormula, String molecularWeight, String stockStatus, String iupacName,
            String synonyms, String smiles, String solvent, String stableIsotopesType, String storageTemp,
            String productFormat, String purity, String shippingTemp) {

        static ProductInfo of(ProductDetail p) {
            return new ProductInfo(true, p.catNo(), p.name(), p.category(), p.apiFamily(), p.casNo(), p.altCasNo(),
                    p.molecularFormula(), p.molecularWeight(), p.stockStatus(), p.iupacName(), p.synonyms(),
                    p.smiles(), p.solvent(), p.stableIsotopesType(), p.storageTemp(), p.productFormat(), p.purity(),
                    p.shippingTemp());
        }
    }

    public record NotFound(boolean found, String message) {

        static NotFound of(String message) {
            return new NotFound(false, message);
        }
    }

    public record Category(String name, long productCount) {
    }

    /** @param price display text such as "$345.00", "contact", or null when prices are not shown */
    public record Pack(String packSize, String price) {
    }

    public record Pricing(String catNo, String status, String currency, List<Pack> packSizes, String gstNote,
            String instruction) {
    }

    public record Documents(String itemCode, String batchNumber, boolean found, List<String> availableDocuments) {
    }

    public record CompanyInfo(String topic, String companyName, String address, String phone, String email,
            String note) {
    }
}
