package br.dev.ctrls.inovareti.domain.report;

import java.awt.Color;
import java.io.ByteArrayInputStream;
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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.FinancialTransaction;
import br.dev.ctrls.inovareti.domain.financeiro.FinancialTransactionRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockBatch;
import br.dev.ctrls.inovareti.domain.inventory.StockBatchRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovement;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    /**
     * Serviço responsável por gerar relatórios Excel para diferentes
     * contextos do sistema (chamados, entradas e saídas de inventário).
     *
     * Os métodos exportam os dados para um {@link java.io.ByteArrayInputStream}
     * pronto para ser retornado em uma resposta HTTP com anexo.
     */

    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final FinancialTransactionRepository transactionRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Chamados");

            // Cria o estilo do cabeçalho
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Cria a linha de cabeçalho
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "Título", "Solicitante", "Setor", "Categoria", "Status", "Prioridade", "Criado Em", "Resolvido Em"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Preenche as linhas de dados
            int rowNum = 1;
            for (Ticket ticket : tickets) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(ticket.getId().toString());
                row.createCell(1).setCellValue(ticket.getTitle());
                row.createCell(2).setCellValue(ticket.getRequester().getName());
                row.createCell(3).setCellValue(ticket.getRequester().getSector().getName());
                row.createCell(4).setCellValue(ticket.getCategory().getName());
                row.createCell(5).setCellValue(ticket.getStatus().toString());
                row.createCell(6).setCellValue(ticket.getPriority().toString());
                row.createCell(7).setCellValue(ticket.getCreatedAt().format(DATE_FORMATTER));
                row.createCell(8).setCellValue(ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : "");
            }

            // Ajusta automaticamente a largura das colunas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Grava no ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for tickets", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    /**
     * Exporta uma lista de lotes de estoque para uma planilha Excel (Entradas).
     *
     * @param batches lista de {@link StockBatch} a serem exportados
     * @return stream contendo o arquivo Excel gerado
     */

    public ByteArrayInputStream exportInventoryEntriesToExcel(List<StockBatch> batches) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Entradas");

            // Cria o estilo do cabeçalho
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Cria a linha de cabeçalho
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Tipo de Item", "Item", "Marca", "Qtd Adquirida", "Fornecedor", "Preço Un.", "Preço Total", "Motivo da Compra", "Data de Entrada"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Preenche as linhas de dados
            int rowNum = 1;
            for (StockBatch batch : batches) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(batch.getItem().getItemCategory().getName());
                row.createCell(1).setCellValue(batch.getItem().getName());
                row.createCell(2).setCellValue(batch.getBrand() != null ? batch.getBrand() : "-");
                row.createCell(3).setCellValue(batch.getOriginalQuantity());
                row.createCell(4).setCellValue(batch.getSupplier() != null ? batch.getSupplier() : "-");
                row.createCell(5).setCellValue(CURRENCY_FORMATTER.format(batch.getUnitPrice()));
                
                BigDecimal totalPrice = batch.getUnitPrice().multiply(BigDecimal.valueOf(batch.getOriginalQuantity()));
                row.createCell(6).setCellValue(CURRENCY_FORMATTER.format(totalPrice));
                
                row.createCell(7).setCellValue(batch.getPurchaseReason() != null ? batch.getPurchaseReason() : "-");
                row.createCell(8).setCellValue(batch.getEntryDate().format(DATE_FORMATTER));
            }

            // Ajusta automaticamente a largura das colunas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Grava no ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for inventory entries", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    /**
     * Exporta saídas de inventário (itens entregues) para uma planilha Excel.
     * Apenas chamados resolvidos com itens solicitados são considerados.
     *
     * @param tickets lista de {@link Ticket} representando saídas
     * @return stream com o arquivo Excel de saídas
     */

    public ByteArrayInputStream exportInventoryExitsToExcel(List<Ticket> tickets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Saídas");

            // Cria o estilo do cabeçalho
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Cria a linha de cabeçalho
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Tipo de Item", "Item", "Qtd Entregue", "Quem Solicitou", "Local do Usuário", "Setor do Usuário", "Preço Total", "Data da Entrega"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Preenche as linhas de dados — apenas chamados resolvidos com itens solicitados
            int rowNum = 1;
            for (Ticket ticket : tickets) {
                if (ticket.getStatus().toString().equals("RESOLVED") && ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(ticket.getRequestedItem().getItemCategory().getName());
                    row.createCell(1).setCellValue(ticket.getRequestedItem().getName());
                    row.createCell(2).setCellValue(ticket.getRequestedQuantity());
                    row.createCell(3).setCellValue(ticket.getRequester().getName());
                    row.createCell(4).setCellValue(ticket.getRequester().getLocation() != null ? ticket.getRequester().getLocation() : "-");
                    row.createCell(5).setCellValue(ticket.getRequester().getSector().getName());
                    
                    // Obtém o valor real do movimento prioritariamente a partir do lançamento financeiro
                    BigDecimal totalPrice = calculateExitTotalPrice(ticket, ticket.getRequestedQuantity() != null ? ticket.getRequestedQuantity() : 0);
                    row.createCell(6).setCellValue(CURRENCY_FORMATTER.format(totalPrice));
                    
                    row.createCell(7).setCellValue(ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : "");
                }
            }

            // Ajusta automaticamente a largura das colunas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Grava no ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report for inventory exits", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    /**
     * Calcula o preço total de uma saída de item com base no preço unitário do lote mais recente.
     * @param item o item sendo retirado
     * @param quantity a quantidade sendo retirada
     * @return preço total (quantidade * preço unitário do lote mais recente), ou ZERO se não houver lotes
     */
    /**
     * Calcula o preço total de uma saída de item priorizando o valor registrado
     * em `financial_transactions.amount` (se existir) ou, alternativamente,
     * somando `unit_price_at_time` dos movimentos (`stock_movements`) relacionados
     * ao chamado. Como fallback final, utiliza o preço do lote mais recente.
     */
    private BigDecimal calculateExitTotalPrice(Ticket ticket, Integer quantity) {
        // 1) Tenta obter lançamentos financeiros vinculados ao ticket
        try {
            var txs = transactionRepository.findByTicketId(ticket.getId());
            if (txs != null && !txs.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (FinancialTransaction tx : txs) {
                    if (tx.getResourceType() == FinancialTransaction.ResourceType.INVENTORY && tx.getAmount() != null) {
                        sum = sum.add(tx.getAmount());
                    }
                }
                if (sum.compareTo(BigDecimal.ZERO) > 0) {
                    return sum;
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar lançamentos financeiros para ticket {}: {}", ticket.getId(), e.getMessage());
        }

        // 2) Fallback: somar unit_price_at_time dos movimentos de estoque referenciando o ticket
        try {
            String prefix = "TICKET:" + ticket.getId();
            List<StockMovement> movements = stockMovementRepository.findByReferenceStartingWithOrderByDateDesc(prefix);
            if (movements != null && !movements.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (StockMovement m : movements) {
                    if (m.getUnitPriceAtTime() != null) {
                        sum = sum.add(m.getUnitPriceAtTime());
                    }
                }
                if (sum.compareTo(BigDecimal.ZERO) > 0) {
                    return sum;
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar movimentos para ticket {}: {}", ticket.getId(), e.getMessage());
        }

        // 3) Fallback final: preço unitário do lote mais recente * quantidade
        var item = ticket.getRequestedItem();
        if (item == null) return BigDecimal.ZERO;

        List<StockBatch> batches = stockBatchRepository.findByItemOrderByEntryDateDesc(item);
        if (batches.isEmpty()) {
            log.warn("No batches found for item {}, returning zero price", item.getId());
            return BigDecimal.ZERO;
        }

        BigDecimal unitPrice = batches.get(0).getUnitPrice();
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        return headerStyle;
    }

    /**
     * Gera um PDF simples com o relatório de saídas de estoque.
     * Retorna o conteúdo em um ByteArrayInputStream pronto para envio.
     */
    public ByteArrayInputStream exportInventoryExitsToPdf(List<Ticket> tickets) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont font = PDType1Font.HELVETICA;
            PDFont bold = PDType1Font.HELVETICA_BOLD;
            float fontSize = 10f;
            float titleFontSize = 16f;
            float headerFontSize = 11f;
            float rowHeight = 18f;

            float margin = 50f;
            PDRectangle mediaBox = page.getMediaBox();
            float pageWidth = mediaBox.getWidth();
            float tableWidth = pageWidth - 2 * margin;
            float startX = mediaBox.getLowerLeftX() + margin;
            float y = mediaBox.getUpperRightY() - margin;

            PDPageContentStream content = new PDPageContentStream(document, page);

            // Title (with Inovare brand color)
            content.setNonStrokingColor(Color.decode("#feb56c"));
            content.beginText();
            content.setFont(bold, titleFontSize);
            content.newLineAtOffset(startX, y);
            content.showText("Inovare Serviços de Saúde");
            content.endText();

            // Underline in brand color
            float titleWidth = (bold.getStringWidth("Inovare Serviços de Saúde") / 1000f) * titleFontSize;
            content.setStrokingColor(Color.decode("#feb56c"));
            content.setLineWidth(1f);
            content.moveTo(startX, y - 4f);
            content.lineTo(startX + titleWidth + 6f, y - 4f);
            content.stroke();

            // Reset color and move down
            content.setNonStrokingColor(Color.BLACK);
            y -= 28f;

            // Prepare rows: filter resolved tickets with items
            java.util.List<Ticket> rows = new ArrayList<>();
            LocalDate periodStart = null;
            LocalDate periodEnd = null;
            for (Ticket t : tickets) {
                if (t.getStatus() != null && "RESOLVED".equals(t.getStatus().toString()) && t.getRequestedItem() != null && t.getRequestedQuantity() != null) {
                    rows.add(t);
                    if (t.getClosedAt() != null) {
                        LocalDate d = t.getClosedAt().toLocalDate();
                        if (periodStart == null || d.isBefore(periodStart)) periodStart = d;
                        if (periodEnd == null || d.isAfter(periodEnd)) periodEnd = d;
                    }
                }
            }

            String periodStr;
            if (periodStart != null && periodEnd != null) {
                var dateOnly = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                periodStr = dateOnly.format(periodStart) + " → " + dateOnly.format(periodEnd);
            } else {
                periodStr = "-";
            }

            // Generation info
            String generatedAt = DATE_FORMATTER.format(LocalDateTime.now());

            // Report subtitle: period and generated at
            content.beginText();
            content.setFont(bold, headerFontSize);
            content.newLineAtOffset(startX, y);
            content.showText("Relatório de Saídas — Período: " + periodStr);
            content.endText();
            y -= 14f;

            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(startX, y);
            content.showText("Gerado em: " + generatedAt);
            content.endText();
            y -= 18f;

            // Table column widths (percentages)
            float[] colPercent = new float[] {0.10f, 0.30f, 0.07f, 0.14f, 0.10f, 0.10f, 0.12f, 0.07f};
            float[] colWidths = new float[colPercent.length];
            for (int i = 0; i < colPercent.length; i++) colWidths[i] = tableWidth * colPercent[i];

            // Draw header background
            float headerHeight = rowHeight;
            float cellX = startX;
            content.setNonStrokingColor(new Color(240, 240, 240));
            for (float w : colWidths) {
                content.addRect(cellX, y - headerHeight, w, headerHeight);
                content.fill();
                cellX += w;
            }
            content.setNonStrokingColor(Color.BLACK);

            // Header texts and borders
            String[] headers = {"Tipo de Item", "Item", "Qtd", "Quem Solicitou", "Local do Usuário", "Setor do Usuário", "Preço Total", "Data da Entrega"};
            cellX = startX;
            content.setStrokingColor(Color.LIGHT_GRAY);
            content.setLineWidth(0.5f);
            for (int i = 0; i < headers.length; i++) {
                // text
                content.beginText();
                content.setFont(bold, fontSize);
                content.newLineAtOffset(cellX + 4f, y - headerHeight + 4f);
                content.showText(headers[i]);
                content.endText();

                // border
                content.addRect(cellX, y - headerHeight, colWidths[i], headerHeight);
                content.stroke();

                cellX += colWidths[i];
            }

            y -= headerHeight;

            // Rows with zebra striping
            for (int idx = 0; idx < rows.size(); idx++) {
                Ticket t = rows.get(idx);

                if (y - margin < rowHeight) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    // reset y to top of new page and redraw header
                    y = page.getMediaBox().getUpperRightY() - margin;

                    // redraw header background
                    cellX = startX;
                    content.setNonStrokingColor(new Color(240, 240, 240));
                    for (float w : colWidths) { content.addRect(cellX, y - headerHeight, w, headerHeight); content.fill(); cellX += w; }
                    content.setNonStrokingColor(Color.BLACK);

                    cellX = startX;
                    content.setStrokingColor(Color.LIGHT_GRAY);
                    content.setLineWidth(0.5f);
                    for (int i = 0; i < headers.length; i++) {
                        content.beginText();
                        content.setFont(bold, fontSize);
                        content.newLineAtOffset(cellX + 4f, y - headerHeight + 4f);
                        content.showText(headers[i]);
                        content.endText();
                        content.addRect(cellX, y - headerHeight, colWidths[i], headerHeight);
                        content.stroke();
                        cellX += colWidths[i];
                    }

                    y -= headerHeight;
                }

                // zebra
                if (idx % 2 == 0) {
                    content.setNonStrokingColor(new Color(250, 250, 250));
                    content.addRect(startX, y - rowHeight, tableWidth, rowHeight);
                    content.fill();
                }

                // cell borders
                cellX = startX;
                content.setStrokingColor(Color.LIGHT_GRAY);
                content.setLineWidth(0.5f);
                for (float w : colWidths) { content.addRect(cellX, y - rowHeight, w, rowHeight); content.stroke(); cellX += w; }

                // prepare cell values
                String tipo = safe(t.getRequestedItem().getItemCategory() != null ? t.getRequestedItem().getItemCategory().getName() : "-");
                String item = safe(t.getRequestedItem().getName());
                String qtd = String.valueOf(t.getRequestedQuantity());
                String requester = safe(t.getRequester() != null ? t.getRequester().getName() : "-");
                String location = t.getRequester() != null && t.getRequester().getLocation() != null ? t.getRequester().getLocation() : "-";
                String sector = t.getRequester() != null && t.getRequester().getSector() != null ? t.getRequester().getSector().getName() : "-";
                BigDecimal totalPrice = calculateExitTotalPrice(t, t.getRequestedQuantity() != null ? t.getRequestedQuantity() : 0);
                String priceStr = CURRENCY_FORMATTER.format(totalPrice);
                String date = t.getClosedAt() != null ? t.getClosedAt().format(DATE_FORMATTER) : "";

                String[] cells = new String[] { tipo, item, qtd, requester, location, sector, priceStr, date };

                // draw cell texts, align qty and price to right
                cellX = startX;
                for (int i = 0; i < cells.length; i++) {
                    if (i == 2 || i == 6) {
                        float textWidth = (font.getStringWidth(cells[i]) / 1000f) * fontSize;
                        float tx = cellX + colWidths[i] - 4f - textWidth;
                        content.beginText();
                        content.setFont(font, fontSize);
                        content.newLineAtOffset(tx, y - rowHeight + 4f);
                        content.showText(cells[i]);
                        content.endText();
                    } else {
                        content.beginText();
                        content.setFont(font, fontSize);
                        content.newLineAtOffset(cellX + 4f, y - rowHeight + 4f);
                        content.showText(cells[i]);
                        content.endText();
                    }
                    cellX += colWidths[i];
                }

                y -= rowHeight;
            }

            // Summary
            int totalItems = 0;
            BigDecimal totalValue = BigDecimal.ZERO;
            for (Ticket t : rows) {
                totalItems += t.getRequestedQuantity() != null ? t.getRequestedQuantity() : 0;
                totalValue = totalValue.add(calculateExitTotalPrice(t, t.getRequestedQuantity() != null ? t.getRequestedQuantity() : 0));
            }

            y -= 8f;
            content.beginText();
            content.setFont(bold, headerFontSize);
            content.newLineAtOffset(startX, y);
            content.showText("Resumo do Período");
            content.endText();
            y -= 14f;

            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(startX, y);
            content.showText("Total de Itens Baixados: " + totalItems);
            content.endText();
            y -= 12f;

            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(startX, y);
            content.showText("Valor Total do Consumo: " + CURRENCY_FORMATTER.format(totalValue));
            content.endText();

            content.close();
            document.save(out);
            document.close();

            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            log.error("Error generating PDF report for inventory exits", e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    private String safe(String s) {
        return s == null ? "-" : s;
    }

    // removed unused truncate helper (no callers)
}
