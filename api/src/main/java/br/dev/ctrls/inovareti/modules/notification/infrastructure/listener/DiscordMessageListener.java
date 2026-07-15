package br.dev.ctrls.inovareti.modules.notification.infrastructure.listener;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.infrastructure.shared.storage.LocalFileStorageService;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.AddTicketCommentUseCase;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketAttachment;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener that listens to messages sent to ticket channels in Discord and integrates them as comments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordMessageListener extends ListenerAdapter {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;
    private final TicketAttachmentRepositoryPort attachmentRepository;
    private final LocalFileStorageService fileStorageService;
    private final AddTicketCommentUseCase addTicketCommentUseCase;

    @Override
    public void onMessageReceived(@javax.annotation.Nonnull MessageReceivedEvent event) {
        // Ignore bots
        if (event.getAuthor().isBot()) {
            return;
        }

        // Process only text channels
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }

        String channelName = event.getChannel().getName();
        if (!channelName.startsWith("ticket-")) {
            return;
        }

        log.info("[DISCORD-TICKET] Nova mensagem recebida no canal {}: {}", channelName, event.getMessage().getContentDisplay());

        String shortId = channelName.substring("ticket-".length());
        if (shortId.length() >= 8) {
            shortId = shortId.substring(0, 8);
        }
        Ticket ticket = ticketRepository.findByShortIdStartingWith(shortId)
                .stream()
                .findFirst()
                .orElse(null);

        if (ticket == null) {
            log.warn("[DISCORD-TICKET] Chamado com ID curto {} não encontrado no banco de dados.", shortId);
            return;
        }

        // Resolve author from sender's Discord ID
        String authorDiscordId = event.getAuthor().getId();
        User author = userRepository.findByDiscordUserId(authorDiscordId)
                .orElseGet(() -> {
                    log.warn("[DISCORD-TICKET] Usuário do Discord {} não está vinculado. Usando fallback.", authorDiscordId);
                    if (ticket.getAssignedTo() != null) {
                        return ticket.getAssignedTo();
                    }
                    return ticket.getRequester();
                });

        // Set Spring SecurityContext temporarily for UseCase execution
        String principal = author.getId().toString();
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, author.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            // Process attachments/prints if any
            List<TicketAttachment> savedAttachments = new ArrayList<>();
            for (var attachment : event.getMessage().getAttachments()) {
                try {
                    byte[] content;
                    try (InputStream is = java.net.URI.create(attachment.getUrl()).toURL().openStream()) {
                        content = is.readAllBytes();
                    }

                    String originalFilename = attachment.getFileName();
                    String contentType = attachment.getContentType();
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }

                    ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(content, originalFilename, contentType);
                    String storedFilename = fileStorageService.store(multipartFile);

                    TicketAttachment ticketAttachment = TicketAttachment.builder()
                            .originalFilename(originalFilename)
                            .storedFilename(storedFilename)
                            .fileType(contentType)
                            .ticket(ticket)
                            .uploadedAt(LocalDateTime.now())
                            .build();

                    TicketAttachment saved = attachmentRepository.save(ticketAttachment);
                    savedAttachments.add(saved);
                    log.info("[DISCORD-TICKET] Anexo {} salvo com sucesso no storage e banco de dados.", originalFilename);
                } catch (Exception ex) {
                    log.error("[DISCORD-TICKET] Falha ao processar anexo {} para o chamado #{}", attachment.getFileName(), ticket.getNumber(), ex);
                }
            }

            // Construct comment content
            String messageText = event.getMessage().getContentDisplay();
            StringBuilder commentContent = new StringBuilder(messageText);

            if (!savedAttachments.isEmpty()) {
                if (commentContent.length() > 0) {
                    commentContent.append("\n\n");
                }
                commentContent.append("**Anexos recebidos via Discord:**");
                for (var att : savedAttachments) {
                    commentContent.append("\n- ").append(att.getOriginalFilename());
                }
            }

            if (commentContent.length() == 0) {
                commentContent.append("Enviou anexo(s) via Discord.");
            }

            TicketCommentRequestDTO commentRequest = new TicketCommentRequestDTO(commentContent.toString());
            addTicketCommentUseCase.execute(ticket.getId(), commentRequest);

            log.info("[DISCORD-TICKET] Comentário integrado com sucesso para o chamado #{}", ticket.getNumber());
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Erro inesperado ao integrar mensagem no chamado #{}", ticket.getNumber(), ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Helper implementation of MultipartFile for passing byte array files to LocalFileStorageService.
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String filename;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String filename, String contentType) {
            this.content = content;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return filename;
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
