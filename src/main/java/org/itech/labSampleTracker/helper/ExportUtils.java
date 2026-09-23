package org.itech.labSampleTracker.helper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.ObjectUtils;

//import com.itextpdf.kernel.colors.ColorConstants;
//import com.itextpdf.kernel.geom.PageSize;
//import com.itextpdf.kernel.pdf.PdfDocument;
//import com.itextpdf.kernel.pdf.PdfWriter;
//import com.itextpdf.layout.Document;
//import com.itextpdf.layout.element.Cell;
//import com.itextpdf.layout.element.Paragraph;
//import com.itextpdf.layout.element.Table;
//import com.itextpdf.layout.element.Text;
//import com.itextpdf.layout.properties.TextAlignment;
//import com.itextpdf.layout.properties.UnitValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportUtils {

	private static Object[] header = { "REGION", "DISTRICT", "SITE DE COLLECTE", "CONVOYEUR", "NATURE DU BILAN",
			"NATURE DU PRELEVEMENT", "CODE DU PATIENT", "DATE DE COLLECTE ECHANTILLON",
			"DATE DE PRELEVEMENT ECHANTILLON", "DESTINATION", "STATUS DE L'ECHANTILLON",
			"RAISON DU REJET DE L'ECHANTILLON", "COMMENTAIRE SUR LE REJET", "LABO AYANT ACCEPTE L'ECHANTILLON",
			"NUMERO LABO", "DATE DE DEPOT DE L'ECHANTILLON", "DATE DE RECEPTION AU LABO RELAIS",
			"DATE DE RECEPTION AU LABORATOIRE FINAL", "DISTANCE PARCOURUE", "DATE DE REALISATION DU TEST",
			"DATE VALIDATION BIOLOGIQUE DU RESULTAT", "DATE DE DISPONIBILITE DES RESULTATS",
			"DATE DE RECUPERATION DU RESULTAT", "DATE DE RENDU DU RESULTAT SUR SITE",
			"DELAI DE TRANSMISSION DES ECHANTILLONS", "DELAI DE RECEPTION DES ECHANTILLONS",
			"DELAI DE TRAITEMENT DES ECHANTILLONS", "DELAI DE COLLECTE DES RESULTATS",
			"DELAI DE TRANSMISSION DES RESULTATS", "DELAI GLOBAL DE RENDU DES RESULTATS (TAT)" };

	public static ByteArrayInputStream writeCSVData(List<Map<String, String>> data) {
		final CSVFormat format = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.MINIMAL);

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (CSVPrinter csvPrinter = new CSVPrinter(new PrintWriter(out), format)) {
				csvPrinter.printRecord(header);

				// int k = 1;
				for (Map<String, String> el : data) {
					Object[] d = { el.get("region"), el.get("district"), el.get("site"), el.get("rider"),
							el.get("sampleType"), el.get("sampleNature"), el.get("patientIdentifier"),
							el.get("pickupDate"), el.get("collectionDate"), el.get("destinationLab"), el.get("status"),
							el.get("rejectionType"), el.get("rejectionComment"),
							ObjectUtils.isNotEmpty(el.get("labName")) ? el.get("labName") : el.get("hubName"),
							el.get("labNumber"), el.get("deliverDate"), el.get("hubAcceptedDate"),
							el.get("labAcceptedDate"), el.get("distance"), el.get("analysisCompletedDate"),
							el.get("analysisReleasedDate"), el.get("resultReportedDate"),
							el.get("resultCollectionDate"), el.get("resultDeliveryDate"),
							el.get("sampleTransmissionDelay"), el.get("sampleReceptionDelay"),
							el.get("sampleProcessingDelay"), el.get("resultCollectionDelay"),
							el.get("resultTransmissionDelay"), el.get("globalDelay") };
					// k++;
					try {
						csvPrinter.printRecord(d);
					} catch (IOException e) {
						log.error("fail to print data in CSV file: ", e);
					}
				}

				csvPrinter.close(true);
			}
			return new ByteArrayInputStream(out.toByteArray());
		} catch (IOException e) {
			log.error("fail to print data to CSV file: ", e);
			return null;
		}
	}
}
