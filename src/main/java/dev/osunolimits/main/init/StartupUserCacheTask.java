package dev.osunolimits.main.init;

import dev.osunolimits.main.init.engine.RunableInitTask;
import dev.osunolimits.modules.utils.UserInfoCache;

public class StartupUserCacheTask extends RunableInitTask {
    @Override
    public void execute() throws Exception {
        UserInfoCache userInfoCache = new UserInfoCache();
        userInfoCache.populateIfNeeded();
        logger.info("User cache initialized");
    }

    @Override
    public String getName() {
        return "StartupUserCacheTask";
    }
}