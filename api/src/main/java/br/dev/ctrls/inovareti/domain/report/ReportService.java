package br.dev.ctrls.inovareti.domain.report;

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
import java.util.Optional;

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

import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

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

                    // Proteção contra NullPointerException ao desencaixar (unboxing) de Integer para int.
                    // O valor pode vir nulo do banco; garantimos 0 como padrão usando Optional.
                    int qty = Optional.ofNullable(ticket.getRequestedQuantity()).orElse(0);

                    row.createCell(0).setCellValue(ticket.getRequestedItem().getItemCategory().getName());
                    row.createCell(1).setCellValue(ticket.getRequestedItem().getName());
                    row.createCell(2).setCellValue(qty);
                    row.createCell(3).setCellValue(ticket.getRequester().getName());
                    row.createCell(4).setCellValue(ticket.getRequester().getLocation() != null ? ticket.getRequester().getLocation() : "-");
                    row.createCell(5).setCellValue(ticket.getRequester().getSector().getName());
                    
                    // Obtém o valor real do movimento prioritariamente a partir do lançamento financeiro
                    BigDecimal totalPrice = calculateExitTotalPrice(ticket, qty);
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
    private BigDecimal calculateExitTotalPrice(Ticket ticket, int quantity) {
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Cor da marca Inovare (#feb56c)
            java.awt.Color inovareColor = new java.awt.Color(254, 181, 108);

            // Logo: tenta carregar de resources (/images/logo.png), se não existir tenta URL pública
            try {
                Image logo = null;
                java.io.InputStream logoStream = ReportService.class.getResourceAsStream("/images/logo.png");
                if (logoStream != null) {
                    byte[] bytes = logoStream.readAllBytes();
                    logo = Image.getInstance(bytes);
                } else {
                    try {
                        // Moderniza criação de URL para evitar uso do construtor deprecated
                        java.net.URL url = java.net.URI.create("https://inovare.med.br/wp-content/uploads/2023/01/Logo.png").toURL();
                        try (java.io.InputStream is = url.openStream()) {
                            byte[] bytes = is.readAllBytes();
                            logo = Image.getInstance(bytes);
                        }
                    } catch (IOException | com.lowagie.text.BadElementException ex) {
                        // Tratamento específico para falhas de IO ou elemento de imagem inválido
                        log.warn("Logo não encontrada em resources e falha ao buscar URL pública: {}", ex.getMessage());
                    }
                }
                if (logo != null) {
                    logo.scaleToFit(140f, 60f);
                    logo.setAlignment(Image.ALIGN_LEFT);
                    document.add(logo);
                }
            } catch (IOException | com.lowagie.text.DocumentException e) {
                // Captura problemas ao adicionar o logo/elementos ao documento PDF
                log.warn("Erro ao inserir logo no PDF: {}", e.getMessage());
            }

            // Título e subtítulo
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, com.lowagie.text.Font.BOLD, inovareColor);
            Paragraph title = new Paragraph("Inovare Serviços de Saúde", titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            document.add(title);

            java.util.List<Ticket> rows = new ArrayList<>();
            LocalDate periodStart = null;
            LocalDate periodEnd = null;
            for (Ticket t : tickets) {
                if (t.getStatus() != null && ("RESOLVED".equals(t.getStatus().toString()) || "CLOSED".equals(t.getStatus().toString())) && t.getRequestedItem() != null && t.getRequestedQuantity() != null) {
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
                periodStr = dateOnly.format(periodStart) + " - " + dateOnly.format(periodEnd);
            } else {
                periodStr = "-";
            }

            com.lowagie.text.Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, com.lowagie.text.Font.NORMAL, java.awt.Color.BLACK);
            // Usa `safe` para garantir valor padrão caso periodStr seja nulo
            Paragraph subtitle = new Paragraph("Relatório de Saídas - Período: " + safe(periodStr) + "    Gerado em: " + DATE_FORMATTER.format(LocalDateTime.now()), subFont);
            subtitle.setSpacingAfter(8f);
            document.add(subtitle);

            // Tabela profissional usando PdfPTable (7 colunas)
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100f);
            // Ajuste de larguras em percentuais (aproximados):
            // Tipo 8%, Item 22%, Qtd 5%, Solicitante 18%, Setor 12%, Preço 15%, Data 20%
            // Distribuição reduz 'Tipo' e 'Qtd' para garantir espaço suficiente na coluna 'Data'
            table.setWidths(new float[] {8f, 22f, 5f, 18f, 12f, 15f, 20f});
            table.setSpacingBefore(6f);

            com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, com.lowagie.text.Font.BOLD, java.awt.Color.WHITE);
            String[] headers = {"Tipo", "Item", "Qtd", "Solicitante", "Setor", "Preço Total", "Data"};
            for (String h : headers) {
                PdfPCell hd = new PdfPCell(new Phrase(h, headerFont));
                hd.setBackgroundColor(inovareColor);
                hd.setBorderWidth(0.5f);
                hd.setPadding(6f);
                hd.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(hd);
            }

            com.lowagie.text.Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10, com.lowagie.text.Font.NORMAL, java.awt.Color.BLACK);
            com.lowagie.text.Font cellFontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, com.lowagie.text.Font.BOLD, java.awt.Color.BLACK);

            BigDecimal tableTotalValue = BigDecimal.ZERO;
            int totalItems = 0;

            for (Ticket t : rows) {
                // Higieniza strings para evitar caracteres problemáticos no PDF
                String requester = sanitizeForPdf(t.getRequester() != null ? t.getRequester().getName() : null);
                String sector = sanitizeForPdf(t.getRequester() != null && t.getRequester().getSector() != null ? t.getRequester().getSector().getName() : null);
                int qty = Optional.ofNullable(t.getRequestedQuantity()).orElse(0);

                BigDecimal totalPrice = BigDecimal.ZERO;
                try {
                    var txs = transactionRepository.findByTicketId(t.getId());
                    if (txs != null && !txs.isEmpty()) {
                        for (FinancialTransaction tx : txs) {
                            if (tx.getAmount() != null && tx.getResourceType() == FinancialTransaction.ResourceType.INVENTORY) {
                                totalPrice = totalPrice.add(tx.getAmount());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao buscar transações para ticket {}: {}", t.getId(), e.getMessage());
                }
                if (totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    totalPrice = calculateExitTotalPrice(t, qty);
                }

                tableTotalValue = tableTotalValue.add(totalPrice);
                totalItems += qty;

                String tipo = sanitizeForPdf(t.getRequestedItem() != null && t.getRequestedItem().getItemCategory() != null ? t.getRequestedItem().getItemCategory().getName() : null);
                String item = sanitizeForPdf(t.getRequestedItem() != null ? t.getRequestedItem().getName() : null);
                String qtd = String.valueOf(qty);
                String priceStr = sanitizeForPdf(CURRENCY_FORMATTER.format(totalPrice));
                String date = sanitizeForPdf(t.getClosedAt() != null ? t.getClosedAt().format(DATE_FORMATTER) : null);

                PdfPCell c1 = new PdfPCell(new Phrase(tipo, cellFont)); c1.setPadding(6f); c1.setHorizontalAlignment(Element.ALIGN_LEFT); table.addCell(c1);
                PdfPCell c2 = new PdfPCell(new Phrase(item, cellFont)); c2.setPadding(6f); c2.setHorizontalAlignment(Element.ALIGN_LEFT); table.addCell(c2);
                PdfPCell c3 = new PdfPCell(new Phrase(qtd, cellFont)); c3.setPadding(6f); c3.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(c3);
                PdfPCell c4 = new PdfPCell(new Phrase(requester, cellFont)); c4.setPadding(6f); c4.setHorizontalAlignment(Element.ALIGN_LEFT); table.addCell(c4);
                PdfPCell c5 = new PdfPCell(new Phrase(sector, cellFont)); c5.setPadding(6f); c5.setHorizontalAlignment(Element.ALIGN_LEFT); table.addCell(c5);
                PdfPCell c6 = new PdfPCell(new Phrase(priceStr, cellFont)); c6.setPadding(6f); c6.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(c6);
                PdfPCell c7 = new PdfPCell(new Phrase(date, cellFont));
                c7.setPadding(6f);
                c7.setHorizontalAlignment(Element.ALIGN_LEFT);
                // Não permitir quebra de linha na coluna 'Data' para manter o formato "dd/MM/yyyy HH:mm" em uma única linha
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

                PdfPCell empty = new PdfPCell(new Phrase("")); empty.setPadding(6f); empty.setBorderWidthTop(1f); table.addCell(empty);
            }

            document.add(table);

            Paragraph resumoTitle = new Paragraph("Resumo do Período", cellFontBold);
            resumoTitle.setSpacingBefore(8f);
            document.add(resumoTitle);

            Paragraph resumo = new Paragraph("Total de Itens Baixados: " + totalItems + "    Valor Total do Consumo: " + CURRENCY_FORMATTER.format(tableTotalValue), cellFont);
            document.add(resumo);

            document.close();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            log.error("Erro ao gerar PDF profissional de saídas", e);
            if (document.isOpen()) document.close();
            throw new RuntimeException("Failed to generate professional PDF report", e);
        }
    }

    private String sanitizeForPdf(String s) {
        if (s == null) return "-";
        String out = s;
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

    private String safe(String s) {
        return s == null ? "-" : s;
    }

    // removido helper de truncamento não utilizado (sem chamadores)
}
