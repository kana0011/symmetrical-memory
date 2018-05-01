package ph.kana.memory.stash.sqljet;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetOptions;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

final class SqlJetStoreConstants {

	private static final String LOCKER_ROOT = System.getProperty("user.home") + System.getProperty("file.separator") + ".pstash";
	static {
		File rootDir = new File(LOCKER_ROOT);
		if (!rootDir.exists()) {
			hideFile(rootDir);
			rootDir.mkdir();
		}
	}

	public static SqlJetDb connection;

	public static final String DB_PATH = String.format("%s/db", LOCKER_ROOT);

	private static final int SQL_USER_VERSION = 1;

	public static SqlJetDb getConnection() throws SqlJetException {
		if (connection == null || !connection.isOpen()) {
			connection = SqlJetDb.open(new File(DB_PATH), true);

			ISqlJetOptions sqlJetOptions = connection.getOptions();
			if (!sqlJetOptions.isAutovacuum()) {
				sqlJetOptions.setAutovacuum(true);
			}

			connection.runTransaction(db -> {
				sqlJetOptions.setUserVersion(SQL_USER_VERSION);
				return null;
			}, SqlJetTransactionMode.WRITE);
		}

		return connection;
	}

	private static void hideFile(File file) {
		String os = System.getProperty("os.name").toLowerCase();

		try {
			if (os.startsWith("windows")) {
				Files.setAttribute(file.toPath(), "dos:hidden", true);
			}
		} catch (IOException e) {}
	}
}