import cc.mallet.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExpData {
	// Use array rather than List for faster processing
	final private String[] genes;
	final private List<int[]> data;

	public ExpData(File data, File genesFile) throws IOException {
		final BufferedReader genesBr = new BufferedReader(new FileReader(genesFile));
		this.genes = genesBr.readLine().split(",");

		String[] rows = FileUtils.readFile(data);
		final int n_cells = rows.length;


		this.data = new ArrayList<>(n_cells);
		for (int i = 0; i < n_cells; i++) {
			String[] rowSplitted = rows[i].split(",");
			this.data.add(new int[genes.length]);
			for (int j = 0; j < genes.length; j++) {
				this.data.get(i)[j] = Integer.parseInt(rowSplitted[j]);
			}
		}
	}

	public String[] getGenes() {
		return genes;
	}

	public List<int[]> getData() {
		return data;
	}
}
