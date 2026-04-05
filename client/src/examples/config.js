'use strict';

const path = require('path');
const BASE_DIR = path.join(__dirname, '../../../test-instance');

module.exports [
    BASE_DIR
]
module.exports = {
    JAR_PATH:      path.join(__dirname, '../../../releases/novacore-engine-v1.1.0.jar'),
    HTTP_PORT:     7878,
    WS_PORT:       7879,
    JAVA_PATH:     'java' || 'C:/Program Files/Java/jre1.8.0_481/bin/javaw.exe',
    MC_VERSION:    'quilt-1.21.6-0.20.0-beta.9',
    SHARED_DIR:    null,
    INSTANCES_DIR: path.join(BASE_DIR),
    LOG_DIR:       path.join(BASE_DIR, 'logs'),
    LAUNCHER_NAME: 'StepLauncher',

    DEFAULT_AUTH: {
        username:    'Stepnicka012',
        // uuid:        '1566d265-5e1d-45b9-a668-70d936b6b891',
        // accessToken: '028ebab6-aede-43f0-869b-e5506687f665',
        // clientId:    'f7c9e8cc-606c-46b5-9001-a9686c8c3ba3',
        userType:    'legacy',
    },

    DEFAULT_WINDOW: {
        width:      1080,
        height:     720,
        fullscreen: false,
    },
};
