package ph.kana.memory.ui.fxml.modal;

import javafx.fxml.FXML;
import ph.kana.memory.account.AccountService;
import ph.kana.memory.stash.StashException;

public class ResetModal extends AbstractTilePaneModal<Void, Void> {

	private final AccountService accountService = AccountService.INSTANCE;

	public ResetModal() {
		super("reset-modal.fxml");
	}

	@Override
	public void showModal(Void data) {
		setVisible(true);
	}

	@FXML
	public void revertApplication() {
		try {
			accountService.purge();
			System.exit(0);
		} catch (StashException e) {
			showBottomFadingText("Setting new PIN failed!");
			e.printStackTrace(System.err);
			close();
		}
	}
}
