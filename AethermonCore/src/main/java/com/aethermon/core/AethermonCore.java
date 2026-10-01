package com.aethermon.core;

import com.aethermon.core.config.CoreConfig;
import com.aethermon.core.economy.EconomyModule;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AethermonCore — entry point.
 * Initialises each module in order. Every module can be
 * disabled individually via config/aethermoncore/config.json.
 */
public class AethermonCore implements ModInitializer {

    public static final String MOD_ID = "aethermoncore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AethermonCore instance;
    private CoreConfig config;
    private EconomyModule economyModule;
    private com.aethermon.core.menu.MenuModule menuModule;
    private com.aethermon.core.homes.HomesModule homesModule;
    private com.aethermon.core.shop.ShopModule shopModule;
    private com.aethermon.core.market.MarketModule marketModule;
    private com.aethermon.core.rewards.RewardsModule rewardsModule;
    private com.aethermon.core.quests.QuestsModule questsModule;
    private com.aethermon.core.luckydraw.LuckyDrawModule luckyDrawModule;
    private com.aethermon.core.worldboss.WorldBossModule  worldBossModule;
    private com.aethermon.core.crates.CratesModule        cratesModule;
    private com.aethermon.core.battlepass.BattlePassModule battlePassModule;
    private com.aethermon.core.duel.DuelModule             duelModule;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("AethermonCore initialising...");

        // Load config first — all modules read from it
        config = CoreConfig.load();

        // Module: Economy (always loaded — everything else depends on it)
        economyModule = new EconomyModule();
        economyModule.init();

        // Module: Homes (/sethome, /home, /delhome, /homes stored in SQLite)
        homesModule = new com.aethermon.core.homes.HomesModule(economyModule.getDatabaseManager());
        homesModule.init();

        // Module: Server Shop (/shop, /sell)
        shopModule = new com.aethermon.core.shop.ShopModule(economyModule.getService());
        shopModule.init();

        // Module: Player Market (/ah, /market)
        marketModule = new com.aethermon.core.market.MarketModule(economyModule.getDatabaseManager(), economyModule.getService());
        marketModule.init();

        // Module: Daily & Online Rewards (/rewards, /daily, /playtime)
        if (config.rewardsEnabled) {
            rewardsModule = new com.aethermon.core.rewards.RewardsModule(economyModule.getDatabaseManager(), economyModule.getService());
            rewardsModule.init();
        }

        // Module: Daily & Weekly Quests (/quests, /quest)
        if (config.questsEnabled) {
            questsModule = new com.aethermon.core.quests.QuestsModule(economyModule.getDatabaseManager(), economyModule.getService());
            questsModule.init();
        }

        // Module: Lucky Draw (/luckydraw, /ld, /gacha)
        if (config.luckyDrawEnabled) {
            luckyDrawModule = new com.aethermon.core.luckydraw.LuckyDrawModule(economyModule.getService());
            luckyDrawModule.init();
        }

        // Module: World Boss (/worldboss, /wbstatus)
        if (config.worldBossEnabled) {
            worldBossModule = new com.aethermon.core.worldboss.WorldBossModule(economyModule.getService());
            worldBossModule.init();
        }

        // Module: Mystery Crates (/crates, /crate, /keys)
        if (config.cratesEnabled) {
            cratesModule = new com.aethermon.core.crates.CratesModule(economyModule.getDatabaseManager(), economyModule.getService());
            cratesModule.init();
        }

        // Module: Battle Pass (/bp, /pass, /battlepass)
        if (config.battlePassEnabled) {
            battlePassModule = new com.aethermon.core.battlepass.BattlePassModule(economyModule.getDatabaseManager(), economyModule.getService());
            battlePassModule.init();
        }

        // Module: Player Duels (/duel)
        if (config.duelEnabled) {
            duelModule = new com.aethermon.core.duel.DuelModule(economyModule.getDatabaseManager(), economyModule.getService());
            duelModule.init();
        }

        // Module: Menu (/menu, /help, /gui server-side chest GUI)
        menuModule = new com.aethermon.core.menu.MenuModule(economyModule.getService());
        menuModule.init();

        // Claims shortcut commands (/claim, /claims, /unclaim)
        com.aethermon.core.claims.ClaimCommands.register();

        LOGGER.info("AethermonCore ready.");
    }

    public static AethermonCore getInstance() { return instance; }
    public CoreConfig getConfig()             { return config; }
    public EconomyModule getEconomyModule()   { return economyModule; }
    public com.aethermon.core.homes.HomesModule getHomesModule() { return homesModule; }
    public com.aethermon.core.shop.ShopModule getShopModule() { return shopModule; }
    public com.aethermon.core.market.MarketModule getMarketModule() { return marketModule; }
    public com.aethermon.core.rewards.RewardsModule getRewardsModule() { return rewardsModule; }
    public com.aethermon.core.quests.QuestsModule getQuestsModule() { return questsModule; }
    public com.aethermon.core.luckydraw.LuckyDrawModule getLuckyDrawModule() { return luckyDrawModule; }
    public com.aethermon.core.worldboss.WorldBossModule  getWorldBossModule()  { return worldBossModule; }
    public com.aethermon.core.crates.CratesModule        getCratesModule()     { return cratesModule; }
    public com.aethermon.core.battlepass.BattlePassModule getBattlePassModule() { return battlePassModule; }
    public com.aethermon.core.duel.DuelModule             getDuelModule()       { return duelModule; }
}
