'use strict';

const path = require('path');
const BASE_DIR = path.join(__dirname, '../../../test-instance');

module.exports = {
    JAR_PATH:      path.join(__dirname, '../../../release/novacore-engine.jar'),
    HTTP_PORT:     7878,
    WS_PORT:       7879,
    MC_VERSION:    '1.21.1',
    SHARED_DIR:    path.join(BASE_DIR, 'shared'),
    INSTANCES_DIR: path.join(BASE_DIR, 'instances'),
    LOG_DIR:       path.join(BASE_DIR, 'logs'),
    LAUNCHER_NAME: 'StepLauncher',
    
    DEFAULT_AUTH: {
        username:    'StepPlayer',
        uuid:        '00000000-0000-0000-0000-000000000001',
        accessToken: '0',
        userType:    'msa',
    },
    
    DEFAULT_JVM: {
        minMemoryMb: 0,
        maxMemoryMb: 0,
    },
    
    DEFAULT_WINDOW: {
        width:      1280,
        height:     720,
        fullscreen: false,
    },
};
