package ph.kana.memory.stash;

import ph.kana.memory.codec.CodecOperationException;
import ph.kana.memory.codec.PasswordCodec;
import ph.kana.memory.codec.PinHasher;
import ph.kana.memory.stash.textfile.AuthFileDao;

public class AuthService {

	private final AuthDao authDao = new AuthFileDao();
	private final PasswordCodec passwordCodec = new PasswordCodec();
	private final PinHasher pinHasher = new PinHasher();

	private final static String DEFAULT_PIN = "12345678";

	public AuthService() {
		try {
			String currentPin = authDao.readStoredPin();
			boolean hasPin = !"".equals(currentPin);
			if (!hasPin) {
				String hashPin = pinHasher.hash(DEFAULT_PIN);
				authDao.savePin(hashPin);
				hasPin = true;
			}
		} catch (StashException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	public boolean checkValidPin(String pin) throws StashException {
		String storedPin = authDao.readStoredPin();
		return pinHasher.validate(pin, storedPin);
	}

	public String decryptPassword(String encryptedPassword, String salt) throws StashException {
		try {
			return passwordCodec.decrypt(encryptedPassword, salt);
		} catch (CodecOperationException e) {
			throw new StashException(e);
		}
	}
}