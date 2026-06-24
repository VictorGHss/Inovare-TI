package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
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
    public byte[] exportInventoryExitsToPdf(List<Ticket> tickets, Map<UUID, BigDecimal> totalsByTicket) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color inovareColor = new java.awt.Color(254, 181, 108);
            addLogo(document);
            addTitle(document, inovareColor);

            List<Ticket> rows = filterResolvedTicketsWithItems(tickets);
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

            for (Ticket ticket : rows) {
                String requester = sanitizeForPdf(ticket.getRequester() != null ? ticket.getRequester().getName() : null);
                String sector = sanitizeForPdf(ticket.getRequester() != null && ticket.getRequester().getSector() != null
                        ? ticket.getRequester().getSector().getName()
                        : null);

                int qty = Optional.ofNullable(ticket.getRequestedQuantity()).orElse(0);
                qty = Math.abs(qty);
                BigDecimal totalPrice = totalsByTicket.getOrDefault(ticket.getId(), BigDecimal.ZERO);

                tableTotalValue = tableTotalValue.add(totalPrice);
                totalItems += qty;

                String tipo = sanitizeForPdf(ticket.getRequestedItem() != null && ticket.getRequestedItem().getItemCategory() != null
                        ? ticket.getRequestedItem().getItemCategory().getName()
                        : null);
                String item = sanitizeForPdf(ticket.getRequestedItem() != null ? ticket.getRequestedItem().getName() : null);
                String qtd = String.valueOf(qty);
                String priceStr = sanitizeForPdf(CURRENCY_FORMATTER.format(totalPrice));
                String date = sanitizeForPdf(ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : null);

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

                PdfPCell c5 = new PdfPCell(new Phrase(sector, cellFont));
                c5.setPadding(6f);
                c5.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c5);

                PdfPCell c6 = new PdfPCell(new Phrase(priceStr, cellFont));
                c6.setPadding(6f);
                c6.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(c6);

                PdfPCell c7 = new PdfPCell(new Phrase(date, cellFont));
                c7.setPadding(6f);
                c7.setHorizontalAlignment(Element.ALIGN_LEFT);
                c7.setNoWrap(true);
                table.addCell(c7);
            }

            if (!rows.isEmpty()) {
                PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", cellFontBold));
                totalLabel.setColspan(5);
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

    @Override
    public byte[] exportAssetMaintenancesToPdf(List<Object[]> consolidation, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color inovareColor = new java.awt.Color(254, 181, 108);
            addLogo(document);
            addTitle(document, inovareColor);

            com.lowagie.text.Font subFont = FontFactory.getFont(
                    FontFactory.HELVETICA,
                    10,
                    com.lowagie.text.Font.NORMAL,
                    java.awt.Color.BLACK);

            DateTimeFormatter dFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            Paragraph subtitle = new Paragraph(
                    "Relatório de Manutenções de Ativos - Período: " + start.toLocalDate().format(dFormatter) + " a " + end.toLocalDate().format(dFormatter)
                            + "    Gerado em: " + LocalDateTime.now().format(DATE_FORMATTER),
                    subFont);
            subtitle.setSpacingAfter(8f);
            document.add(subtitle);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100f);
            table.setWidths(new float[] { 25f, 40f, 15f, 20f });
            table.setSpacingBefore(6f);

            com.lowagie.text.Font headerFont = FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    11,
                    com.lowagie.text.Font.BOLD,
                    java.awt.Color.WHITE);

            String[] headers = { "Código Patrimônio", "Nome do Ativo", "Intervenções", "Soma dos Custos" };
            for (String header : headers) {
                PdfPCell hd = new PdfPCell(new Phrase(header, headerFont));
                hd.setBackgroundColor(inovareColor);
                hd.setBorderWidth(0.5f);
                hd.setPadding(6f);
                hd.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(hd);
            }

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

            BigDecimal grandTotalCost = BigDecimal.ZERO;
            long grandTotalCount = 0;

            for (Object[] row : consolidation) {
                String patrimonyCode = sanitizeForPdf((String) row[0]);
                String assetName = sanitizeForPdf((String) row[1]);
                long count = ((Number) row[2]).longValue();
                BigDecimal totalCost = (BigDecimal) row[3];

                grandTotalCost = grandTotalCost.add(totalCost);
                grandTotalCount += count;

                PdfPCell c1 = new PdfPCell(new Phrase(patrimonyCode, cellFont));
                c1.setPadding(6f);
                c1.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c1);

                PdfPCell c2 = new PdfPCell(new Phrase(assetName, cellFont));
                c2.setPadding(6f);
                c2.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c2);

                PdfPCell c3 = new PdfPCell(new Phrase(String.valueOf(count), cellFont));
                c3.setPadding(6f);
                c3.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(c3);

                PdfPCell c4 = new PdfPCell(new Phrase(CURRENCY_FORMATTER.format(totalCost), cellFont));
                c4.setPadding(6f);
                c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(c4);
            }

            if (!consolidation.isEmpty()) {
                PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", cellFontBold));
                totalLabel.setColspan(2);
                totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalLabel.setPadding(6f);
                totalLabel.setBorderWidthTop(1f);
                table.addCell(totalLabel);

                PdfPCell totalCountCell = new PdfPCell(new Phrase(String.valueOf(grandTotalCount), cellFontBold));
                totalCountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalCountCell.setPadding(6f);
                totalCountCell.setBorderWidthTop(1f);
                table.addCell(totalCountCell);

                PdfPCell totalCostCell = new PdfPCell(new Phrase(CURRENCY_FORMATTER.format(grandTotalCost), cellFontBold));
                totalCostCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalCostCell.setPadding(6f);
                totalCostCell.setBorderWidthTop(1f);
                table.addCell(totalCostCell);
            }

            document.add(table);

            Paragraph resumoTitle = new Paragraph("Resumo do Período", cellFontBold);
            resumoTitle.setSpacingBefore(8f);
            document.add(resumoTitle);

            Paragraph resumo = new Paragraph(
                    "Total de Intervenções: " + grandTotalCount
                            + "    Custo Total das Manutenções: " + CURRENCY_FORMATTER.format(grandTotalCost),
                    cellFont);
            document.add(resumo);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            log.error("Erro ao gerar PDF de manutenções de ativos", e);
            if (document.isOpen()) {
                document.close();
            }
            throw new RuntimeException("Failed to generate asset maintenance PDF report", e);
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

    private List<Ticket> filterResolvedTicketsWithItems(List<Ticket> tickets) {
        List<Ticket> rows = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (ticket.getStatus() != null
                    && ("RESOLVED".equals(ticket.getStatus().toString()) || "CLOSED".equals(ticket.getStatus().toString()))
                    && ticket.getRequestedItem() != null
                    && ticket.getRequestedQuantity() != null) {
                rows.add(ticket);
            }
        }
        return rows;
    }

    private String resolvePeriodText(List<Ticket> rows) {
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        for (Ticket ticket : rows) {
            if (ticket.getClosedAt() != null) {
                LocalDate date = ticket.getClosedAt().toLocalDate();
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
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100f);
        table.setWidths(new float[] { 8f, 22f, 5f, 18f, 12f, 15f, 20f });
        table.setSpacingBefore(6f);

        com.lowagie.text.Font headerFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                11,
                com.lowagie.text.Font.BOLD,
                java.awt.Color.WHITE);

        String[] headers = { "Tipo", "Item", "Qtd", "Solicitante", "Setor", "Preço Total", "Data" };
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
