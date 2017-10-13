package ph.kana.memory.stash;

import ph.kana.memory.model.Account;

import java.util.List;

public interface AccountDao {

	List<Account> fetchAll() throws StashException;

	Account save(Account account) throws StashException;
}