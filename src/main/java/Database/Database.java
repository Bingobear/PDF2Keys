package Database;

import java.lang.AutoCloseable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import Database.model.*;
import master.keyEx.models.Corpus;
import master.keyEx.models.PDF;
import master.keyEx.models.WordOcc;

//TODO HCICORPUS ->CORPUS (later)
public class Database {
	private Connection connect = null;
	private String dbName = "hciCorpus";
	private Statement statement = null;
	private PreparedStatement preparedStatement = null;
	private ResultSet resultSet = null;

	// you need to close all three to make sure
	private void close() {
		close(resultSet);
		close(statement);
		close(connect);
	}

	private void close(AutoCloseable c) {
		try {
			if (c != null) {
				c.close();
			}
		} catch (Exception e) {
			// don't throw now as it might leave following closables in
			// undefined state
		}
	}

	public void fillDB(PDF pdf, Corpus corpus) throws SQLException,
			ClassNotFoundException {
		// this will load the MySQL driver, each DB has its own driver
		Class.forName("com.mysql.jdbc.Driver");
		// setup the connection with the DB.
		connect = DriverManager.getConnection("jdbc:mysql://localhost/"
				+ dbName + "?" + "user=test&password=test");
		Statement stmt = connect.createStatement();
		int idPub = -1;
		String sqlT = "SELECT idPublication,title FROM " + dbName
				+ ".Publication";
		ResultSet rsT = stmt.executeQuery(sqlT);
		while (rsT.next()) {
			int id = rsT.getInt("idPublication");
			String title = rsT.getString("title");
			if (pdf.getFirstPage().contains(title)) {
				idPub = id;
				// System.out.println("FOUND Title - " + title);
				break;
			}
		}
		rsT.close();
		ArrayList<Integer> authors = new ArrayList<Integer>();
		if (idPub < 0) {
			// not in BTH database
			String sql = "SELECT idAuthor,name FROM " + dbName + ".Author";
			ResultSet rs = stmt.executeQuery(sql);
			// STEP 5: Extract data from result set
			ArrayList<String> author = new ArrayList<String>();
			ArrayList<Integer> positions = new ArrayList<Integer>();
			ArrayList<Author> authorsall = createAllAuthors();
			for (Author auth : authorsall) {
				// while (rs.next()) {
				// // Retrieve by column name
				// int id = rs.getInt("idAuthor");
				// String name = rs.getString("name");
				ArrayList<String> nameparts = new ArrayList<String>();
				for (String retval : auth.getName().split(",")) {
					nameparts.add(retval);
				}
				// if(name.equals("Thombansen, Ulrich")){
				// String stop = "kacke";
				// // name="Thombansen";
				// }
				// String eval = pdf.getFirstPage();
				// //\\p{L}
				// eval = eval.replaceAll("[^\\p{L}]"," ");
				//
				// for (int count = 0; count < nameparts.size()-1; count++) {
				// if(eval.matches(".*\\b"+nameparts.get(count)+"\\b.*")){
				// // if (pdf.getFirstPage().contains(nameparts.get(count))) {
				// authors.add(auth.getAuthorID());
				// System.out.println(nameparts.get(count));
				// // System.out.println("FOUND Author - " + name
				// // + pdf.getFirstPage().substring(0, 10));
				// }
				// }
				// if(authors.isEmpty()){
				for (int count = 0; count < nameparts.size() - 1; count++) {

					if (pdf.getFirstPage().contains(nameparts.get(count))) {

						authors.add(auth.getAuthorID());
						positions.add(pdf.getFirstPage().indexOf(
								nameparts.get(count)));
						author.add(nameparts.get(count));

						// System.out.println("FOUND Author - " + name
						// + pdf.getFirstPage().substring(0, 10));
					}
				}
				// }

			}
			// Create subfunction
			HashSet<Integer> uniqueValues = new HashSet<Integer>(positions);
			if (uniqueValues.size() < positions.size()) {
				ArrayList<Integer> ids = new ArrayList<Integer>();
				for (int ii = 0; ii < positions.size(); ii++) {
					for (int jj = ii + 1; jj < positions.size(); jj++) {
						if (positions.get(jj).equals(positions.get(ii))) {
							if (author.get(jj).length() > author.get(ii)
									.length()) {
								author.remove(ii);
								authors.remove(ii);
								positions.remove(ii);
							} else {
								author.remove(jj);
								authors.remove(jj);
								positions.remove(jj);
							}
						}
					}
				}
			}
			rs.close();
			fillADB(pdf, authors, corpus);
		} else {
			// found title in database
			fillTDB(pdf, idPub, corpus);
		}
	}

	private ArrayList<Author> createAllAuthors() throws SQLException {

		ArrayList<Author> authors = new ArrayList<Author>();
		Statement state = connect.createStatement();

		// state.setFetchSize(100);
		ResultSet resultSetCategory = state.executeQuery("SELECT * FROM  "
				+ dbName + ".author");
		while (resultSetCategory.next()) {

			int id = resultSetCategory.getInt("idAuthor");
			// System.out.println(id);
			String name = resultSetCategory.getString("name");
			// String nameNorm = Normalizer.normalize(name,
			// Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
			Author aut = new Author(name, id);
			// System.out.println(pdf.getPublicationID());
			authors.add(aut);
		}
		resultSetCategory.close();
		state.close();
		return authors;

	}

	private void fillTDB(PDF pdf, int idPub, Corpus corpus) throws SQLException {
		long pdfID = -1;
		long corpID = -1;
		corpID = addCorpus(corpus);

		if (pdf != null) {
			pdfID = addTPDF(corpID, idPub, pdf);

			if (pdfID > 0) {
				if (!pdf.getWordOccList().isEmpty()) {
					ArrayList<Integer> defKeys = addCategory(pdf, pdfID);

					ArrayList<WordOcc> words = pdf.getWordOccList();
					ArrayList<Integer> genKeys = addKeywords(words, corpID,
							pdfID);

					addCathasKeys(defKeys, genKeys, pdfID);
				}

			}
		}

	}

	private void fillADB(PDF pdf, ArrayList<Integer> authors, Corpus corpus)
			throws SQLException {
		long pdfID = 0;
		long corpID = 0;
		corpID = addCorpus(corpus);

		if (pdf != null) {
			pdfID = addPDF(corpID, pdf);

			if (pdfID > 0) {
				// TODO Author duplicates care
				addPDFhasAuth(authors, pdfID);

				if (!pdf.getWordOccList().isEmpty()) {
					// TODO CHANGE UNIQUE CATEGORIES
					// ArrayList<Long> keyIDs = new ArrayList<Long>();
					ArrayList<Integer> defKeys = addCategory(pdf, pdfID);

					ArrayList<WordOcc> words = pdf.getWordOccList();
					ArrayList<Integer> genKeys = addKeywords(words, corpID,
							pdfID);

					addCathasKeys(defKeys, genKeys, pdfID);

				}
			}
		}

	}

	// TODO for some reason not all cats are added
	// TODO different solution to duplicates ! testing if connection already
	// exists
	private void addCathasKeys(ArrayList<Integer> defKeys,
			ArrayList<Integer> genKeys, long pdfID) throws SQLException {
		for (int ii = 0; ii < defKeys.size(); ii++) {
			for (int jj = 0; jj < genKeys.size(); jj++) {
				// TODO duplicate!!!
				preparedStatement = connect.prepareStatement("insert into  "
						+ dbName + ".KEYWORD_has_Category values (?, ?)"
						+ " ON DUPLICATE KEY UPDATE Category_idCategory=?",
						Statement.RETURN_GENERATED_KEYS);

				if (defKeys.isEmpty()) {
					preparedStatement.setInt(1, genKeys.get(jj));
					preparedStatement.setNull(2, java.sql.Types.INTEGER);
					preparedStatement.setInt(3, defKeys.get(ii));

				} else {
					preparedStatement.setInt(1, genKeys.get(jj));
					preparedStatement.setInt(2, defKeys.get(ii));
					preparedStatement.setInt(3, defKeys.get(ii));

				}
				try {
					preparedStatement.executeUpdate();

				} catch (Exception e) {
					// System.out.println(pdfID);
					e.printStackTrace();
				}
			}
		}

	}

	// ALSO GIVE CAT IDF AND SO ON
	private ArrayList<Integer> addKeywords(ArrayList<WordOcc> words,
			long corpID, long pdfID) throws SQLException {
		ArrayList<Integer> genKeys = new ArrayList<Integer>();
		for (int ii = 0; ii < words.size(); ii++) {
			preparedStatement = connect.prepareStatement("insert into  "
					+ dbName + ".Keyword values (default,?, ?,?,?,?,?,?)",
					Statement.RETURN_GENERATED_KEYS);
			preparedStatement.setInt(1, (int) corpID);
			preparedStatement.setInt(2, (int) pdfID);

			preparedStatement.setString(3, words.get(ii).getWord().getWord());
			preparedStatement.setInt(4, words.get(ii).getOcc());
			preparedStatement.setDouble(5, words.get(ii).getTfidf());
			preparedStatement.setDouble(6, words.get(ii).getIdf());
			preparedStatement.setDouble(7, words.get(ii).getTf());
			preparedStatement.executeUpdate();

			ResultSet rs = null;
			try {
				rs = preparedStatement.getGeneratedKeys();
				if (rs.next()) {
					genKeys.add((int) rs.getLong(1));
				}
				rs.close();
			} finally {
				// ... cleanup that will execute whether or not an
				// error
				// occurred ...
			}

		}

		return genKeys;
	}

	// TODO EXTEND CATEGORY AND RELEVANCE in main code and DB
	private ArrayList<Integer> addCategory(PDF pdf, long pdfID)
			throws SQLException {
		ArrayList<Integer> defKeys = new ArrayList<Integer>();
		if (!pdf.getGenericKeywords().isEmpty()) {
			for (int count = 0; count < pdf.getGenericKeywords().size(); count++) {
				int idDef = -1;
				String sqlT = "SELECT idCategory,name,normtitle FROM " + dbName
						+ ".Category";
				Statement stmt = connect.createStatement();
				ResultSet rsT = stmt.executeQuery(sqlT);
				while (rsT.next()) {
					int id = rsT.getInt("idCategory");
					String title = rsT.getString("name");
					String normtitle = rsT.getString("normtitle");

					// first case not nec is in second
					if (pdf.getGenericKeywords().get(count).getNormtitle()
							.equals(normtitle)
							|| (pdf.getGenericKeywords().get(count)
									.getAssociatedGCAT().equals(normtitle))) {
						idDef = id;
						// System.out.println("FOUND Category - " + title);
						break;
					}
				}
				rsT.close();
				if (idDef < 0) {
					preparedStatement = connect.prepareStatement(
							"insert into  " + dbName
									+ ".CATEGORY values (default,?,?,?,?)",
							Statement.RETURN_GENERATED_KEYS);
					preparedStatement.setString(1, pdf.getGenericKeywords()
							.get(count).getTitle());
					preparedStatement.setDouble(2, pdf.getGenericKeywords()
							.get(count).getRelevance());
					preparedStatement.setString(3, pdf.getGenericKeywords()
							.get(count).getNormtitle());
					preparedStatement.setString(4, pdf.getGenericKeywords()
							.get(count).getAssociatedGCAT());

					try {
						preparedStatement.executeUpdate();

					} catch (Exception e) {
						// System.out.println(pdfID);
						e.printStackTrace();
					}
					ResultSet rs = null;
					try {
						rs = preparedStatement.getGeneratedKeys();
						if (rs.next()) {
							defKeys.add((int) rs.getLong(1));
						}
						rs.close();
					} finally {
						// ... cleanup that will execute whether or
						// not
						// an
						// error
						// occurred ...
					}
				} else {
					defKeys.add(idDef);
				}
			}
		}
		addPDFCat(defKeys, pdfID);
		return defKeys;
	}

	private void addPDFCat(ArrayList<Integer> defKeys, long pdfID)
			throws SQLException {
		for (int jj = 0; jj < defKeys.size(); jj++) {
			// on Duplicate ?
			preparedStatement = connect.prepareStatement("insert into  "
					+ dbName + ".PDF_has_Category values (?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			preparedStatement.setInt(1, (int) pdfID);
			preparedStatement.setInt(2, defKeys.get(jj));

			try {
				preparedStatement.executeUpdate();

			} catch (Exception e) {
				// System.out.println(pdfID);
				e.printStackTrace();
			}

		}

	}

	private long addTPDF(long corpID, int idPub, PDF pdf) throws SQLException {
		int pdfID = -1;
		preparedStatement = connect.prepareStatement("insert into  " + dbName
				+ ".PDF values (default, ?,?, ?,?,?)"
				+ " ON DUPLICATE KEY update wordcount=?",
				Statement.RETURN_GENERATED_KEYS);
		preparedStatement.setInt(1, (int) corpID);
		preparedStatement.setInt(2, (int) idPub);
		preparedStatement.setString(2, pdf.getFirstPage().substring(0, 200));
		preparedStatement.setInt(3, pdf.getWordcount());
		preparedStatement.setString(4, pdf.getLanguage());
		preparedStatement.setInt(5, pdf.getWordcount());
		preparedStatement.executeUpdate();

		ResultSet rs = null;

		try {
			rs = preparedStatement.getGeneratedKeys();
			if (rs.next()) {
				pdfID = (int) rs.getLong(1);
			}
			rs.close();
		} finally {
			// ... cleanup that will execute whether or not an error
			// occurred ...
		}
		return pdfID;
	}

	private void addPDFhasAuth(ArrayList<Integer> authors, long pdfID)
			throws SQLException {

		for (int jj = 0; jj < authors.size(); jj++) {
			// on Duplicate ?
			preparedStatement = connect.prepareStatement("insert into  "
					+ dbName + ".PDF_has_Author values (?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			preparedStatement.setInt(1, (int) pdfID);
			preparedStatement.setInt(2, authors.get(jj));

			try {
				preparedStatement.executeUpdate();

			} catch (Exception e) {
				// System.out.println(pdfID);
				e.printStackTrace();
			}

		}

	}

	private long addPDF(long corpID, PDF pdf) throws SQLException {
		int pdfID = -1;
		preparedStatement = connect.prepareStatement("insert into " + dbName
				+ ".PDF values (default,?, ?, ?,?,?,?,?)"
				+ " ON DUPLICATE KEY update wordcount=?",
				Statement.RETURN_GENERATED_KEYS);
		preparedStatement.setInt(1, (int) corpID);
		// TODO setNULL
		preparedStatement.setNull(2, java.sql.Types.INTEGER);
		// pdf.getFirstPage().substring(0, 100)
		preparedStatement.setString(3, pdf.getTitle());
		preparedStatement.setInt(4, pdf.getWordcount());
		preparedStatement.setString(5, pdf.getLanguage());
		preparedStatement.setInt(6, pdf.getPagecount());
		// TODO COLUMN COUNT DOESNT MATCH VALUE COUNT AT ROW 1
		preparedStatement.setString(7, pdf.getFilename());
		preparedStatement.setInt(8, pdf.getWordcount());
		try {
			preparedStatement.executeUpdate();
			ResultSet rs = null;
			rs = preparedStatement.getGeneratedKeys();
			if (rs.next()) {
				pdfID = (int) rs.getLong(1);
			}

		} catch (Exception e) {
			System.out.println(pdf.getTitle() + " ID: " + pdfID);
			e.printStackTrace();
		}
		return pdfID;
	}

	private long addCorpus(Corpus corpus) throws SQLException {
		int corpID = -1;
		if (corpus != null) {

			Statement stmt = connect.createStatement();
			String sqlT = "SELECT id FROM " + dbName + ".corpus";
			ResultSet rsT = stmt.executeQuery(sqlT);

			if (rsT.next()) {
				corpID = (int) rsT.getLong(1);
			} else {
				preparedStatement = connect.prepareStatement("insert into  "
						+ dbName + ".CORPUS values (default,?, ?,?)"
						+ " ON DUPLICATE KEY update uniqueRow=?",
						Statement.RETURN_GENERATED_KEYS);
				// TODO include both formats of language DOC N !!!
				preparedStatement.setInt(1, corpus.getDocN("de"));
				preparedStatement.setInt(2, corpus.getDocN("en"));
				preparedStatement.setString(3, "yes");
				preparedStatement.setString(4, "yes");

				try {
					preparedStatement.executeUpdate();
					ResultSet rs = preparedStatement.getGeneratedKeys();
					if (rs.next()) {
						corpID = (int) rs.getLong(1);
					}
				} catch (Exception e) {
					// System.out.println(corpus.getDocN());
					e.printStackTrace();
				}
				// onDuplicate increase occurence
				addGlobalCategory(corpus, corpID);
			}
		}

		return corpID;
	}

	// TODO SAVE GLOBAL RELEVANCE ?
	// TODO DUPLICATE PDF
	private void addGlobalCategory(Corpus corpus, int corpID)
			throws SQLException {
		// NOT NECESSARY
		ArrayList<Integer> gCids = new ArrayList<Integer>();
		Statement stmt = connect.createStatement();
		String sqlT = "SELECT idGlobalCategory, title,normtitle FROM " + dbName
				+ ".GlobalCategory";
		ResultSet rsT = stmt.executeQuery(sqlT);
		int id = -1;

		boolean found = false;
		for (int ii = 0; ii < corpus.getGlobalCategoryCatalog().size(); ii++) {
			if (rsT.next()) {
				id = rsT.getInt("idGlobalCategory");
			}
			if (id != -1) {
				while (rsT.next()) {
					String normtitle = rsT.getString("normtitle");

					if (corpus.getGlobalCategoryCatalog().get(ii).getCategory()
							.getNormtitle().contains(normtitle)) {
						gCids.add(id);

						addCatKeywords(id, corpus.getGlobalCategoryCatalog()
								.get(ii).getKeywordList());
						// System.out.println("FOUND Category - " + title);
						found = true;
						break;
					}

				}
			}
			if (!found) {

				preparedStatement = connect.prepareStatement("insert into  "
						+ dbName + ".GlobalCategory values (default,?, ?,?)",
						Statement.RETURN_GENERATED_KEYS);
				preparedStatement.setInt(1, corpID);
				preparedStatement.setString(2, corpus
						.getGlobalCategoryCatalog().get(ii).getCategory()
						.getTitle());
				preparedStatement.setString(3, corpus
						.getGlobalCategoryCatalog().get(ii).getCategory()
						.getNormtitle());
				try {
					preparedStatement.executeUpdate();
					ResultSet rs = preparedStatement.getGeneratedKeys();
					if (rs.next()) {
						id = (int) rs.getLong(1);
						gCids.add(id);
					}
				} catch (Exception e) {
					// System.out.println(corpus.getDocN());
					e.printStackTrace();
				}

				addCatKeywords(id, corpus.getGlobalCategoryCatalog().get(ii)
						.getKeywordList());
			} else {
				found = false;
			}
		}

	}

	// addCatKeywords()
	// TODO Auto-generated method stub

	// geht das so ?=
	// ALSO GIVE IDF TF AND SO ON ?
	private void addCatKeywords(int id, ArrayList<WordOcc> keywordList)
			throws SQLException {

		for (int ii = 0; ii < keywordList.size(); ii++) {
			preparedStatement = connect.prepareStatement("insert into "
					+ dbName + ".Cat_Keyw values (default,?, ?,?,?)"
					+ " ON DUPLICATE KEY update occ=occ+"
					+ keywordList.get(ii).getOcc(),
					Statement.RETURN_GENERATED_KEYS);

			preparedStatement.setInt(1, id);
			preparedStatement.setString(2, keywordList.get(ii).getWord()
					.getWord());
			preparedStatement.setInt(3, keywordList.get(ii).getOcc());
			preparedStatement.setDouble(4, keywordList.get(ii).getCatTFIDF());
			try {
				preparedStatement.executeUpdate();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
}
