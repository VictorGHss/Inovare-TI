package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import br.dev.ctrls.inovareti.modules.report.application.dto.OutflowReportRowDTO;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportPdfExporterPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de infraestrutura especializado em relatórios PDF físicos.
 * Implementa o contrato puro Java do domínio utilizando iText/OpenPDF.
 */
@Component
@Slf4j
public class ReportPdfExporter implements ReportPdfExporterPort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    @Override
    public byte[] exportInventoryExitsToPdf(List<OutflowReportRowDTO> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color inovareColor = new java.awt.Color(254, 181, 108);
            addLogo(document);
            addTitle(document, inovareColor);

            String periodStr = resolvePeriodText(rows);

            com.lowagie.text.Font subFont = FontFactory.getFont(
                    FontFactory.HELVETICA,
                    10,
                    com.lowagie.text.Font.NORMAL,
                    java.awt.Color.BLACK);

            Paragraph subtitle = new Paragraph(
                    "Relatório de Saídas - Período: " + safe(periodStr)
                            + "    Gerado em: " + DATE_FORMATTER.format(LocalDateTime.now()),
                    subFont);
            subtitle.setSpacingAfter(8f);
            document.add(subtitle);

            PdfPTable table = createTableWithHeader(inovareColor);

            com.lowagie.text.Font cellFont = FontFactory.getFont(
                    FontFactory.HELVETICA,
                    10,
                    com.lowagie.text.Font.NORMAL,
                    java.awt.Color.BLACK);
            com.lowagie.text.Font cellFontBold = FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    10,
                    com.lowagie.text.Font.BOLD,
                    java.awt.Color.BLACK);

            BigDecimal tableTotalValue = BigDecimal.ZERO;
            int totalItems = 0;

            for (OutflowReportRowDTO row : rows) {
                String requester = sanitizeForPdf(row.requester());
                String sector = sanitizeForPdf(row.userSector());
                String location = sanitizeForPdf(row.userLocation());

                int qty = Optional.ofNullable(row.quantity()).orElse(0);
                qty = Math.abs(qty);
                BigDecimal totalPrice = Optional.ofNullable(row.totalPrice()).orElse(BigDecimal.ZERO);

                tableTotalValue = tableTotalValue.add(totalPrice);
                totalItems += qty;

                String tipo = sanitizeForPdf(row.itemType());
                String item = sanitizeForPdf(row.item());
                String qtd = String.valueOf(qty);
                String priceStr = sanitizeForPdf(CURRENCY_FORMATTER.format(totalPrice));
                String date = sanitizeForPdf(row.deliveryDate() != null ? row.deliveryDate().format(DATE_FORMATTER) : null);

                PdfPCell c1 = new PdfPCell(new Phrase(tipo, cellFont));
                c1.setPadding(6f);
                c1.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c1);

                PdfPCell c2 = new PdfPCell(new Phrase(item, cellFont));
                c2.setPadding(6f);
                c2.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c2);

                PdfPCell c3 = new PdfPCell(new Phrase(qtd, cellFont));
                c3.setPadding(6f);
                c3.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(c3);

                PdfPCell c4 = new PdfPCell(new Phrase(requester, cellFont));
                c4.setPadding(6f);
                c4.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c4);

                PdfPCell c5 = new PdfPCell(new Phrase(location, cellFont));
                c5.setPadding(6f);
                c5.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c5);

                PdfPCell c6 = new PdfPCell(new Phrase(sector, cellFont));
                c6.setPadding(6f);
                c6.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c6);

                PdfPCell c7 = new PdfPCell(new Phrase(priceStr, cellFont));
                c7.setPadding(6f);
                c7.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(c7);

                PdfPCell c8 = new PdfPCell(new Phrase(date, cellFont));
                c8.setPadding(6f);
                c8.setHorizontalAlignment(Element.ALIGN_LEFT);
                c8.setNoWrap(true);
                table.addCell(c8);
            }

            if (!rows.isEmpty()) {
                PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", cellFontBold));
                totalLabel.setColspan(6);
                totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalLabel.setPadding(6f);
                totalLabel.setBorderWidthTop(1f);
                table.addCell(totalLabel);

                PdfPCell totalValueCell = new PdfPCell(new Phrase(CURRENCY_FORMATTER.format(tableTotalValue), cellFontBold));
                totalValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalValueCell.setPadding(6f);
                totalValueCell.setBorderWidthTop(1f);
                table.addCell(totalValueCell);

                PdfPCell empty = new PdfPCell(new Phrase(""));
                empty.setPadding(6f);
                empty.setBorderWidthTop(1f);
                table.addCell(empty);
            }

            document.add(table);

            Paragraph resumoTitle = new Paragraph("Resumo do Período", cellFontBold);
            resumoTitle.setSpacingBefore(8f);
            document.add(resumoTitle);

            Paragraph resumo = new Paragraph(
                    "Total de Itens Baixados: " + totalItems
                            + "    Valor Total do Consumo: " + CURRENCY_FORMATTER.format(tableTotalValue),
                    cellFont);
            document.add(resumo);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            log.error("Erro ao gerar PDF profissional de saídas", e);
            if (document.isOpen()) {
                document.close();
            }
            throw new RuntimeException("Failed to generate professional PDF report", e);
        }
    }

    private void addLogo(Document document) {
        try {
            Image logo = null;
            try (java.io.InputStream logoStream = ReportPdfExporter.class.getResourceAsStream("/images/logo.png")) {
                if (logoStream != null) {
                    byte[] bytes = logoStream.readAllBytes();
                    logo = Image.getInstance(bytes);
                } else {
                    try {
                        java.net.URL url = java.net.URI
                                .create("https://inovare.med.br/wp-content/uploads/2023/01/Logo.png")
                                .toURL();
                        try (java.io.InputStream is = url.openStream()) {
                            byte[] bytes = is.readAllBytes();
                            logo = Image.getInstance(bytes);
                        }
                    } catch (IOException | com.lowagie.text.BadElementException ex) {
                        log.warn("Logo não encontrada em resources e falha ao buscar URL pública: {}", ex.getMessage());
                    }
                }
            }

            if (logo != null) {
                logo.scaleToFit(140f, 60f);
                logo.setAlignment(Image.ALIGN_LEFT);
                document.add(logo);
            }
        } catch (IOException | DocumentException e) {
            log.warn("Erro ao inserir logo no PDF: {}", e.getMessage());
        }
    }

    private void addTitle(Document document, java.awt.Color inovareColor) throws DocumentException {
        com.lowagie.text.Font titleFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                16,
                com.lowagie.text.Font.BOLD,
                inovareColor);
        Paragraph title = new Paragraph("Inovare Serviços de Saúde", titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        document.add(title);
    }

    private String resolvePeriodText(List<OutflowReportRowDTO> rows) {
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        for (OutflowReportRowDTO row : rows) {
            if (row.deliveryDate() != null) {
                LocalDate date = row.deliveryDate().toLocalDate();
                if (periodStart == null || date.isBefore(periodStart)) {
                    periodStart = date;
                }
                if (periodEnd == null || date.isAfter(periodEnd)) {
                    periodEnd = date;
                }
            }
        }

        if (periodStart != null && periodEnd != null) {
            DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return dateOnly.format(periodStart) + " - " + dateOnly.format(periodEnd);
        }

        return "-";
    }

    private PdfPTable createTableWithHeader(java.awt.Color inovareColor) throws DocumentException {
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100f);
        table.setWidths(new float[] { 12f, 22f, 5f, 15f, 12f, 12f, 10f, 12f });
        table.setSpacingBefore(6f);

        com.lowagie.text.Font headerFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                11,
                com.lowagie.text.Font.BOLD,
                java.awt.Color.WHITE);

        String[] headers = { "Tipo de Item", "Item", "Qtd Entregue", "Quem Solicitou", "Local do Usuário", "Setor do Usuário", "Preço Total", "Data da Entrega" };
        for (String header : headers) {
            PdfPCell hd = new PdfPCell(new Phrase(header, headerFont));
            hd.setBackgroundColor(inovareColor);
            hd.setBorderWidth(0.5f);
            hd.setPadding(6f);
            hd.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(hd);
        }

        return table;
    }

    private String sanitizeForPdf(String value) {
        if (value == null) {
            return "-";
        }

        String out = value;
        out = out.replace("→", "->");
        out = out.replace("—", "-");
        out = out.replace("–", "-");
        out = out.replace("…", "...");
        out = out.replace("•", "-");
        out = out.replace("\u2018", "'");
        out = out.replace("\u2019", "'");
        out = out.replace("\u201C", "\"");
        out = out.replace("\u201D", "\"");
        return out;
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
