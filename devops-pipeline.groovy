
n = new com.mirantis.mk.Common()

/**
 * Creates env according to input params by DevOps tool
 * 
 * @param path Path to dos.py 
 * @param work_dir path where devops is installed
 * @param type Path to template having been created
 */
def createDevOpsEnv(path, work_dir, tpl){
    echo "${path} ${tpl}"
    return sh(script:"""
    export ENV_NAME=${params.ENV_NAME} &&
    export WORKING_DIR=${$work_dir} &&
    export DEVOPS_DB_NAME=$WORKING_DIR/fuel-devops.sqlite &&
    export DEVOPS_DB_ENGINE=django.db.backends.sqlite3 && 
    ${path} create-env ${tpl}
    """, returnStdout: true)
}

/**
 * Erases the env 
 *
 * @param path Path to dos.py  
 * @param env name of the ENV have to be deleted 
  */
def eraseDevOpsEnv(path, env){
    echo "${env} will be erased"
}

/**
 * Starts the env 
 * 
 * @param path Path to dos.py 
 * @param env name of the ENV have to be brought up 
  */
def startupDevOpsEnv(path, env){
    return sh(script:"""
    ${path} start ${env}
    """, returnStdout: true)
}

/**
 * Get env IP 
 * 
 * @param path Path to dos.py 
 * @param env name of the ENV to find out IP 
  */
def getDevOpsIP(path, env){
    return sh(script:"""
    ${path} slave-ip-list --address-pool-name public-pool01 --ip-only ${env}
    """, returnStdout: true)
}

def ifEnvIsReady(envip){
    def retries = 50
    if (retries != -1){
        retry(retries){
            return sh(script:"""
            nc -z -w 30 ${envip} 22
            """, returnStdout: true)
        }
    } else {
        echo "It seems the env has not been started properly"
    }    
}


node {
    devops_dos_path = '/var/fuel-devops-venv/fuel-devops-venv/bin/dos.py'
    devops_work_dir = '/var/fuel-devops-venv'
    stage ('Creating environmet') {
        if ("${params.ENV_NAME}" == '') {
            error("ENV_NAME have to be defined")
        }
        echo "${params.ENV_NAME} ${params.TEMPLATE}"
        if ("${params.TEMPLATE}" == 'Single') {
            echo "Single"
            tpl = '/var/fuel-devops-venv/tpl/clound-init-single.yaml'
        } else if ("${params.TEMPLATE}" == 'Multi') {
            echo "Multi"
        }
        try {
            createDevOpsEnv("${devops_dos_path}","${devops_work_dir}","${tpl}")
        } catch (err) {
            error("${err}")
//            eraseDevOpsEnv("${params.ENV_NAME}")   
        }
    }
     stage ('Bringing up the environment') {
        try {
            startupDevOpsEnv("${devops_dos_path}","${params.ENV_NAME}")
        } catch (err) {
            error("${params.ENV_NAME} has not been managed to bring up")
        }
     }
     stage ('Getting environment IP') {
        try {
            envip = getDevOpsIP("${devops_dos_path}","${params.ENV_NAME}").trim()
            echo "${envip}"
        } catch (err) {
            error("IP of the env ${params.ENV_NAME} can't be got")
        }                
    }
     stage ('Checking whether the env has finished starting') {
         ifEnvIsReady("${envip}")
     }
    
}

