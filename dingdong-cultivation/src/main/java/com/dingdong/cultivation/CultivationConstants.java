package com.dingdong.cultivation;

/**
 * 修仙系统共享常量。
 */
public final class CultivationConstants {

    private CultivationConstants() {}

    public static final String[][] REALMS = {
        {"mortal", "凡人", "0"},
        {"lianqi", "练气", "100"},
        {"zhuji", "筑基", "300"},
        {"jindan", "金丹", "600"},
        {"yuanying", "元婴", "1000"},
        {"huashen", "化神", "1500"},
        {"dujie", "渡劫", "2200"},
        {"dacheng", "大乘", "3000"},
        {"zhenxian", "真仙", "4000"},
    };

    public static final String[] SUB_LEVEL_NAMES = {"", "初期", "中期", "后期", "圆满"};
    public static final double[] REALM_COEFF = {1.0, 1.2, 1.5, 1.8, 2.2, 2.6, 3.0, 3.5, 4.0};
    public static final int MIN_REALM_FOR_SECT = 3;
    public static final int CREATE_SECT_COST = 500;
    public static final int[] LEVEL_UP_COSTS = {0, 0, 500, 1200, 2500, 4500, 7000, 10000, 14000, 20000};
    public static final int MIN_CULTIVATION_COST = 50;

    public static int getRealmIndex(String realm) {
        for (int i = 0; i < REALMS.length; i++) {
            if (REALMS[i][0].equals(realm)) return i;
        }
        return 0;
    }

    public static String realmDisplayName(String realm, int subLevel) {
        int idx = getRealmIndex(realm);
        return REALMS[idx][1] + "·" + SUB_LEVEL_NAMES[subLevel];
    }

    public static double realmCoeff(String realm) {
        return REALM_COEFF[getRealmIndex(realm)];
    }

    public static int getLevelUpCost(int currentLevel) {
        return currentLevel < LEVEL_UP_COSTS.length ? LEVEL_UP_COSTS[currentLevel] : 99999;
    }

    public static String cultivationUsersDdl() {
        return "CREATE TABLE IF NOT EXISTS cultivation_users (" +
            "user_id INTEGER NOT NULL," +
            "group_id INTEGER NOT NULL DEFAULT 0," +
            "user_name TEXT DEFAULT ''," +
            "realm TEXT NOT NULL DEFAULT 'mortal'," +
            "sub_level INTEGER DEFAULT 1," +
            "cultivation INTEGER DEFAULT 0," +
            "root_bone INTEGER DEFAULT 10," +
            "luck INTEGER DEFAULT 10," +
            "spirit INTEGER DEFAULT 10," +
            "spirit_stones INTEGER DEFAULT 100," +
            "reputation INTEGER DEFAULT 0," +
            "last_cultivate_time TEXT DEFAULT ''," +
            "last_dual_cultivate_time TEXT DEFAULT ''," +
            "last_checkin_date TEXT DEFAULT ''," +
            "is_injured INTEGER DEFAULT 0," +
            "injury_until TEXT DEFAULT ''," +
            "has_reborn INTEGER DEFAULT 0," +
            "has_tribulation_pill INTEGER DEFAULT 0," +
            "has_rebirth_pill INTEGER DEFAULT 0," +
            "created_at TEXT DEFAULT ''," +
            "PRIMARY KEY (user_id, group_id))";
    }
}
