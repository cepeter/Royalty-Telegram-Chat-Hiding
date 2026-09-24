package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class TelegramCompatibilityProbeTest {
    private static final class DialogSearch {
        int r0;
        List<Object> s;
        List<Object> F;
        List<Object> s0;
        List<Object> t0;
        List<Object> u0;
        Object w0;

        void U(int folder, String query) {}
        Object J(int index) { return null; }
        int h() { return 0; }
    }

    private static final class DialogSearchView {
        void l() {}
    }

    private static final class Recent {
        Object a;
        int b;
        long c;
    }

    private static final class SearchHelper {
        int m;
        List<Object> d;
        List<Object> e;
        Object f;
        List<Object> g;
        Object h;
        Object i;
        List<Object> j;
        List<Object> k;
        List<Object> l;
    }

    private static final class ShareAlert {
        Object J;
        Object K;
        Object L;
        Object T;
    }

    private static final class ShareList {
        List<Object> d;
        Object e;
    }

    private static final class ShareSearch {
        List<Object> d;
        Object e;
        void E(String query) {}
    }

    private static final class ShareRow {
        Object a;
        Object b;
        int c;
        CharSequence d;
    }

    private static final class Contacts {
        Object r;
        Object d;
    }

    private static final class ContactSearch {
        List<Object> d;
        List<Object> e;
        Object f;
        List<Object> G;
    }

    private static final class ContactList {
        List<Object> y;
    }

    private static final class GroupActivity {
        Object v;
    }

    private static final class GroupAdapter {
        List<Object> d;
        List<Object> e;
        Object f;
        List<Object> r;
        void L(String query) {}
    }

    @Test
    public void verifiesAllMappedTelegram12104Surfaces() throws Exception {
        Map<String, Class<?>> classes = new HashMap<>();
        classes.put("we.b0", DialogSearch.class);
        classes.put("org.telegram.ui.Components.eo0", DialogSearchView.class);
        classes.put("we.a0", Recent.class);
        classes.put("we.n1", SearchHelper.class);
        classes.put("org.telegram.ui.Components.wq0", ShareAlert.class);
        classes.put("org.telegram.ui.Components.oq0", ShareList.class);
        classes.put("org.telegram.ui.Components.sq0", ShareSearch.class);
        classes.put("org.telegram.ui.Components.kq0", ShareRow.class);
        classes.put("org.telegram.ui.ContactsActivity", Contacts.class);
        classes.put("org.telegram.ui.mt", ContactSearch.class);
        classes.put("org.telegram.ui.nt", ContactList.class);
        classes.put("org.telegram.ui.s70", GroupActivity.class);
        classes.put("org.telegram.ui.q70", GroupAdapter.class);

        TelegramCompatibilityProbe.verify(name -> {
            Class<?> type = classes.get(name);
            if (type == null) throw new ClassNotFoundException(name);
            return type;
        });
    }

    @Test
    public void rejectsPartialMappings() {
        assertThrows(
                NoSuchFieldException.class,
                () -> TelegramCompatibilityProbe.verify(name -> Object.class));
    }
}
