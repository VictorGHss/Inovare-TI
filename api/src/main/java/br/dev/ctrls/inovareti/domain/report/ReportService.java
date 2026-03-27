package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
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
                    BigDecimal totalPrice = calculateExitTotalPrice(ticket, ticket.getRequestedQuantity());
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
            PDPage page = new PDPage();
            document.addPage(page);

            PDFont font = PDType1Font.HELVETICA;
            PDFont bold = PDType1Font.HELVETICA_BOLD;
            float fontSize = 10f;
            float leading = 1.2f * fontSize;

            float margin = 50f;
            PDRectangle mediaBox = page.getMediaBox();
            float startX = mediaBox.getLowerLeftX() + margin;
            float startY = mediaBox.getUpperRightY() - margin;
            float y = startY;

            PDPageContentStream content = new PDPageContentStream(document, page);

            // Title
            content.beginText();
            content.setFont(bold, 14f);
            content.newLineAtOffset(startX, y);
            content.showText("Relatório de Saídas de Estoque");
            content.endText();
            y -= 25f;

            // Header line
            content.beginText();
            content.setFont(bold, fontSize);
            content.newLineAtOffset(startX, y);
            String[] headers = {"Tipo", "Item", "Qtd", "Solicitante", "Local", "Setor", "Preço Total", "Data"};
            content.showText(String.join(" | ", headers));
            content.endText();
            y -= leading;

            // Rows
            for (Ticket ticket : tickets) {
                if (ticket.getStatus().toString().equals("RESOLVED") && ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
                    String tipo = safe(ticket.getRequestedItem().getItemCategory() != null ? ticket.getRequestedItem().getItemCategory().getName() : "-");
                    String item = safe(ticket.getRequestedItem().getName());
                    String qtd = String.valueOf(ticket.getRequestedQuantity());
                    String requester = safe(ticket.getRequester() != null ? ticket.getRequester().getName() : "-");
                    String location = ticket.getRequester() != null && ticket.getRequester().getLocation() != null ? ticket.getRequester().getLocation() : "-";
                    String sector = ticket.getRequester() != null && ticket.getRequester().getSector() != null ? ticket.getRequester().getSector().getName() : "-";
                    BigDecimal totalPrice = calculateExitTotalPrice(ticket, ticket.getRequestedQuantity());
                    String priceStr = CURRENCY_FORMATTER.format(totalPrice);
                    String date = ticket.getClosedAt() != null ? ticket.getClosedAt().format(DATE_FORMATTER) : "";

                    String line = String.format("%s | %s | %s | %s | %s | %s | %s | %s",
                            truncate(tipo, 20), truncate(item, 40), qtd, truncate(requester, 20), truncate(location, 15), truncate(sector, 15), priceStr, date);

                    if (y < margin + 50f) {
                        content.close();
                        page = new PDPage();
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = page.getMediaBox().getUpperRightY() - margin;
                    }

                    content.beginText();
                    content.setFont(font, fontSize);
                    content.newLineAtOffset(startX, y);
                    content.showText(line);
                    content.endText();
                    y -= leading;
                }
            }

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

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
