package ph.kana.memory.ui.fxml;

import javafx.application.HostServices;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import ph.kana.memory.account.AccountService;
import ph.kana.memory.account.CorruptDataException;
import ph.kana.memory.auth.AuthService;
import ph.kana.memory.model.Account;
import ph.kana.memory.stash.StashException;
import ph.kana.memory.type.LoginFlag;
import ph.kana.memory.type.SortColumn;
import ph.kana.memory.ui.fxml.message.LargeCenterText;
import ph.kana.memory.ui.fxml.modal.*;
import ph.kana.memory.ui.fxml.widget.AccountCardPane;
import ph.kana.memory.ui.model.AccountCard;
import ph.kana.memory.ui.model.AccountComparator;

import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MainFormController implements Initializable {

	private final Logger logger = Logger.getLogger(MainFormController.class.getName());

	private HostServices hostServices;
	private IdleMonitor idleMonitor = null;

	private final AccountService accountService = AccountService.INSTANCE;
	private final AuthService authService = AuthService.INSTANCE;

	private Comparator<Account> activeComparator = AccountComparator.of((SortColumn.DATE_ADDED));
	private SortedMap<Account, AccountCard> accountCards =
			new TreeMap<>(activeComparator);

	private final Duration SESSION_EXPIRE_DURATION = Duration.seconds(30);

	@FXML private Pane rootPane;
	@FXML private Pane viewPane;

	@FXML private TextField filterTextBox;

	@FXML private ToggleGroup sortGroup;

	@FXML
	public void showAddAccountDialog() {
		var saveAccountModal = new SaveAccountModal();
		saveAccountModal.setOnClose(account -> {
			if (Objects.nonNull(account)) {
				var accountCard = renderAccountCard(account);
				accountCards.put(account, accountCard);

				filterAccounts(filterTextBox.getText());
			}
		});
		showModal(saveAccountModal, null);
	}

	@FXML
	public void showSetPinModal() {
		showModal(new SavePinModal(), null);
	}

	@FXML
	public void openSourceCodeUrl() {
		String url = "https://gitlab.com/kana0011/symmetrical-memory";
		showBottomMessage("Opening web browser...");
		hostServices.showDocument(url);
	}

	@FXML
	public void clearSearchFilter() {
		filterTextBox.setText("");
		filterAccounts("");
	}

	@FXML
	public void clearClipboard() {
		Clipboard.getSystemClipboard()
				.clear();
		UiCommons.showBottomFadingText("Clipboard cleared!", rootPane.getChildren());
	}

	@FXML
	public void showCreateBackupModal() {
		showModal(new CreateBackupModal(), null);
	}

	@FXML
	public void showRestoreBackupModal() {
		showModal(new RestoreBackupModal(), null);
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		showLoginModal(true);

		filterTextBox.textProperty()
				.addListener((observable, oldValue, newValue) -> filterAccounts(newValue));

		sortGroup.selectedToggleProperty()
				.addListener((observable, oldValue, newValue) -> updateSort());
	}

	public void setHostServices(HostServices hostServices) {
		this.hostServices = hostServices;
	}

	private void filterAccounts(String searchString) {
		viewPane.getChildren()
				.clear();
		showCenterMessage("Searching...");

		if (searchString.isEmpty()) {
			accountCards.values()
					.forEach(this::renderAccountCard);
			attemptShowingNoAccountMessage();
		} else {
			var filteredAccounts = accountCards.keySet()
					.stream()
					.filter(account -> matchesDomainOrUsername(account, searchString))
					.map(accountCards::get)
					.collect(Collectors.toUnmodifiableList());
			if (filteredAccounts.isEmpty()) {
				showCenterMessage(String.format("No search results for\n'%s'", cutString(searchString)));
			} else {
				filteredAccounts
						.forEach(this::renderAccountCard);
				clearCenterMessage();
			}
		}
	}

	private void updateSort() {
		var selectedSort = sortGroup.getSelectedToggle();
		var data = selectedSort.getUserData().toString();
		var sortColumn = SortColumn.valueOf(data);

		activeComparator = AccountComparator.of(sortColumn);

		var updatedSortMap = new TreeMap<Account, AccountCard>(activeComparator);
		updatedSortMap.putAll(accountCards);
		accountCards = updatedSortMap;

		viewPane.getChildren()
				.clear();
		renderAccounts(accountCards.keySet());
	}

	private void loadAccounts() {
		viewPane.getChildren()
				.clear();
		showCenterMessage("Loading...");
		try {
			var accounts = accountService.fetchAccounts();
			renderAccounts(accounts);

			attemptShowingNoAccountMessage();
		} catch (StashException e) {
			showBottomMessage("Loading failed!");
			logger.severe(e::getMessage);
		} catch (CorruptDataException e) {
			handleCorruptDb(e);
		}
	}

	private void renderAccounts(Collection<Account> accounts) {
		clearCenterMessage();
		accounts.stream()
				.map(this::renderAccountCard)
				.forEach(accountCard -> accountCards.put(accountCard.getAccount(), accountCard));
	}

	private AccountCard renderAccountCard(Account account) {
		var pane = new AccountCardPane(account, rootPane, this::handleCorruptDb);
		pane.setOnDeleteAccount(this::attemptShowingNoAccountMessage);

		var insertIndex = calculateInsertIndex(account);
		viewPane.getChildren()
				.add(insertIndex, pane);

		return new AccountCard(pane, account);
	}

	private AccountCard renderAccountCard(AccountCard accountCard) {
		var createdAccountCard = renderAccountCard(accountCard.getAccount());
		accountCard.setCard(createdAccountCard.getCard());
		return accountCard;
	}

	private int calculateInsertIndex(Account account) {
		var childNodes = viewPane.getChildren();

		if (childNodes.isEmpty()) {
			return 0;
		}

		var lowerBound = 0;
		var upperBound = childNodes.size() - 1;
		int index;

		for (;;) {
			index = (upperBound + lowerBound) / 2;

			var sampleAccount = (Account) childNodes.get(index).getUserData();
			var compare = activeComparator
					.compare(sampleAccount, account);
			if (0 == compare) {
				return index;
			} else if (0 > compare) {
				lowerBound = index + 1;
				if (lowerBound > upperBound) {
					return index + 1;
				}
			} else {
				upperBound = index - 1;
				if (lowerBound > upperBound) {
					return index;
				}
			}
		}
	}

	private <T, R> void showModal(AbstractTilePaneModal<T, R> modal, T data) {
		List<Node> rootChildren = rootPane.getChildren();
		rootChildren.add(modal);
		UiCommons.assignAnchors(modal, 0.0, 0.0, 0.0, 0.0);

		modal.setOnHandleCorruptDb(this::handleCorruptDb);
		modal.showModal(data);
	}

	private void showLoginModal(boolean startup) {
		try {
			boolean pinExists = authService.initializePin();

			var loginModal = new LoginModal();
			var flag = LoginFlag.REGULAR;

			if (startup) {
				loginModal.setOnClose(r -> {
					loadAccounts();
					initializeIdleMonitor();
				});
				if (!pinExists) {
					flag = LoginFlag.FIRST_TIME;
				}
			} else {
				idleMonitor.stopMonitoring();
				loginModal.setOnClose(r -> idleMonitor.startMonitoring());
				flag = LoginFlag.SESSION_EXPIRE;
			}

			showModal(loginModal, flag);
		} catch (CorruptDataException e) {
			handleCorruptDb(e);
		}
	}

	private void showCenterMessage(String message) {
		LargeCenterText centerText = new LargeCenterText();
		centerText.setMessage(message);

		clearCenterMessage();
		List<Node> rootChildren = rootPane.getChildren();
		rootChildren.add(2, centerText);
		UiCommons.assignAnchors(centerText, 50.0, 0.0, 0.0, 0.0);
	}

	private void clearCenterMessage() {
		rootPane.getChildren()
				.removeIf(LargeCenterText.class::isInstance);
	}

	private void showBottomMessage(String message) {
		UiCommons.showBottomFadingText(message, rootPane.getChildren());
	}

	private void initializeIdleMonitor() {
		if (null == idleMonitor) {
			idleMonitor = new IdleMonitor(SESSION_EXPIRE_DURATION, () -> showLoginModal(false));
			idleMonitor.register(rootPane.getScene(), Event.ANY);
			idleMonitor.startMonitoring();
		}
	}

	private String cutString(String string) {
		if (string.length() > 18) {
			return string.substring(0, 18) + '\u2026';
		}
		return string;
	}

	private boolean matchesDomainOrUsername(Account account, String string) {
		var search = string.toLowerCase();
		return account.getDomain().toLowerCase()
				.contains(search) ||
			account.getUsername().toLowerCase()
				.contains(search);
	}

	private void handleCorruptDb(CorruptDataException e) {
		showModal(new ResetModal(), null);
		logger.severe(e::getMessage);
	}

	private void attemptShowingNoAccountMessage() {
		if (accountCards.isEmpty()) {
			showCenterMessage("No saved accounts!\nClick 'Add' to get started!");
		} else {
			clearCenterMessage();
		}
	}
}
