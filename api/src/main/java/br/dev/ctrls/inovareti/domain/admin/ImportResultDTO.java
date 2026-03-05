package br.dev.ctrls.inovareti.domain.admin;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for CSV import result statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {
    
    private boolean success;
    private int usersCreated;
    private int sectorsCreated;
    private int assetsCreated;
    private int categoriesCreated;
    private List<String> errors = new ArrayList<>();
    
    public void incrementUsersCreated() {
        this.usersCreated++;
    }
    
    public void incrementSectorsCreated() {
        this.sectorsCreated++;
    }
    
    public void incrementAssetsCreated() {
        this.assetsCreated++;
    }
    
    public void incrementCategoriesCreated() {
        this.categoriesCreated++;
    }
}
