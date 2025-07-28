package org.apache.fineract.infrastructure.report.dto;

import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FeicReport {
    
    private ArrayList<Titular> titulares;    
    private ArrayList<Producto> productos;    
}
