package br.dev.ctrls.inovareti.domain.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelas operações de persistência durante a importação CSV.
 * Encapsula toda a lógica de "find-or-create" para Usuários, Setores, Categorias e Ativos.
 * Parsing de strings → {@link CsvRowParser}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvPersistenceService {

    private final UserRepositoryPort userRepository;
    private final SectorRepositoryPort sectorRepository;
    private final AssetRepositoryPort assetRepository;
    private final AssetCategoryRepositoryPort assetCategoryRepository;
    private final AssetMaintenanceRepositoryPort assetMaintenanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final CsvRowParser csvRowParser;

    private static final String DEFAULT_PASSWORD = "Mudar@123";
    private static final String DEFAULT_LOCATION = "Matriz";

    /**
     * Encontra o usuário pelo e-mail ou cria um novo, incluindo setor.
     */
    public User findOrCreateUser(CsvImportRow referenceRow, List<CsvImportRow> userRows, ImportResultDTO result) {
        User existingUser = userRepository.findByEmail(referenceRow.userEmail()).orElse(null);
        if (existingUser != null) {
            return existingUser;
        }

        String userName = csvRowParser.firstNonBlank(userRows, CsvImportRow::userName)
                .orElseThrow(() -> new IllegalArgumentException("UserName é obrigatório para criar novo usuário"));
        String sectorName = csvRowParser.firstNonBlank(userRows, CsvImportRow::sectorName)
                .orElseThrow(() -> new IllegalArgumentException("SectorName é obrigatório para criar novo usuário"));
        String userRoleStr = csvRowParser.firstNonBlank(userRows, CsvImportRow::userRoleStr).orElse("USER");

        Sector sector = sectorRepository.findByName(sectorName)
                .orElseGet(() -> {
                    Sector newSector = Sector.builder().name(sectorName).build();
                    Sector savedSector = sectorRepository.save(newSector);
                    result.incrementSectorsCreated();
                    log.debug("Created new sector: {}", sectorName);
                    return savedSector;
                });

        UserRole role = csvRowParser.parseUserRole(userRoleStr);

        User newUser = User.builder()
                .name(userName)
                .email(referenceRow.userEmail())
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .mustChangePassword(true)
                .role(role)
                .sector(sector)
                .location(DEFAULT_LOCATION)
                .build();

        try {
            User savedUser = userRepository.save(newUser);
            result.incrementUsersCreated();
            log.debug("Created new user: {}", referenceRow.userEmail());
            return savedUser;
        } catch (DataIntegrityViolationException ex) {
            // Blindagem para concorrência: reutiliza o registro criado por outro processo.
            return userRepository.findByEmail(referenceRow.userEmail()).orElseThrow(() -> ex);
        }
    }

    /**
     * Processa a criação do ativo de uma linha CSV para um usuário já persistido.
     * Linhas sem nome ou código de patrimônio são ignoradas silenciosamente.
     */
    public void processAssetRow(CsvImportRow row, User user, ImportResultDTO result) {
        if (row.assetName().isEmpty() || row.patrimonyCode().isEmpty()) {
            return;
        }
        processAsset(row.assetName(), row.assetCategoryName(), row.patrimonyCode(), row.assetSpecs(), user, result);
    }

    private void processAsset(String assetName, String assetCategoryName, String patrimonyCode,
                               String assetSpecs, User user, ImportResultDTO result) {
        if (assetRepository.existsByPatrimonyCode(patrimonyCode)) {
            log.debug("Ativo com código de patrimônio {} já existe — ignorando", patrimonyCode);
            return;
        }

        AssetCategory category = null;
        if (!assetCategoryName.isEmpty()) {
            category = assetCategoryRepository.findByName(assetCategoryName)
                    .orElseGet(() -> {
                        AssetCategory newCat = AssetCategory.builder().name(assetCategoryName).build();
                        AssetCategory saved = assetCategoryRepository.save(newCat);
                        result.incrementCategoriesCreated();
                        log.debug("Created new asset category: {}", assetCategoryName);
                        return saved;
                    });
        }

        Asset asset = Asset.builder()
                .users(Set.of(user))
                .name(assetName)
                .patrimonyCode(patrimonyCode)
                .category(category)
                .specifications(assetSpecs.isEmpty() ? null : assetSpecs)
                .build();

        Asset savedAsset;
        try {
            savedAsset = assetRepository.save(asset);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Ativo com código de patrimônio {} criado concorrentemente. Ignorando.", patrimonyCode);
            return;
        }
        result.incrementAssetsCreated();
        log.debug("Created new asset: {} ({})", assetName, patrimonyCode);

        // Cria registro de auditoria (AssetMaintenance)
        AssetMaintenance maintenance = AssetMaintenance.builder()
                .asset(savedAsset)
                .maintenanceDate(LocalDate.now())
                .type(AssetMaintenance.MaintenanceType.TRANSFER)
                .description("Carga inicial de dados via importação CSV. Atribuído a " + user.getName())
                .cost(BigDecimal.ZERO)
                .technician(user)
                .build();
        assetMaintenanceRepository.save(maintenance);
        log.debug("Created audit record for asset: {}", patrimonyCode);
    }
}
