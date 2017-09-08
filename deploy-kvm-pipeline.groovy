common = new com.mirantis.mk.Common()

/**
* ENV_NAME              The prefix for env name is going to be created
* TEMPLATE              There are two templates are available for one-node installation and two-node (Single or Multi)
* DESTROY_ENV           To shutdown env once job is finished
* DEPLOY_OPENSTACK      if set True OpenStack will be deployed
* SLAVE_NODE            The node where VM is going to be created
* JOB_DEP_NAME          The node where VM is going to be created
*/


/**
 * Creates env according to input params by DevOps tool
 * 
 * @param path Path to dos.py 
 * @param work_dir path where devops is installed
 * @param type Path to template having been created
 */
def createDevOpsEnv(path, work_dir, tpl, env){
//    echo "${path} ${tpl}"
    return sh(script:"""
    export ENV_NAME=${env} &&
    export WORKING_DIR=${work_dir} &&
    export DEVOPS_DB_NAME=${work_dir}/fuel-devops.sqlite &&
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
    return sh(script:"""
    export ENV_NAME=${env} &&
    export WORKING_DIR=${work_dir} &&
    export DEVOPS_DB_NAME=${work_dir}/fuel-devops.sqlite &&
    export DEVOPS_DB_ENGINE=django.db.backends.sqlite3 && 
    ${path} erase ${env}
    """, returnStdout: true)
}

/**
 * Shutdown the env 
 *
 * @param path Path to dos.py  
 * @param env name of the ENV have to be destroyed 
  */
def destroyDevOpsEnv(path, env){
    return sh(script:"""
    export ENV_NAME=${env} &&
    export WORKING_DIR=${work_dir} &&
    export DEVOPS_DB_NAME=${work_dir}/fuel-devops.sqlite &&
    export DEVOPS_DB_ENGINE=django.db.backends.sqlite3 && 
    ${path} destroy ${env}
    """, returnStdout: true)    
}

/**
 * Starts the env 
 * 
 * @param path Path to dos.py 
 * @param work_dir path where devops is installed
 * @param env name of the ENV have to be brought up 
  */
def startupDevOpsEnv(path, work_dir, env){
    return sh(script:"""
    export ENV_NAME=${env} &&
    export WORKING_DIR=${work_dir} &&
    export DEVOPS_DB_NAME=${work_dir}/fuel-devops.sqlite &&
    export DEVOPS_DB_ENGINE=django.db.backends.sqlite3 && 
    ${path} start ${env}
    """, returnStdout: true)
}

/**
 * Get env IP 
 * 
 * @param path Path to dos.py 
 * @param work_dir path where devops is installed
 * @param env name of the ENV to find out IP 
  */
def getDevOpsIP(path, work_dir, env){
    return sh(script:"""
    export ENV_NAME=${env} &&
    export WORKING_DIR=${work_dir} &&
    export DEVOPS_DB_NAME=${work_dir}/fuel-devops.sqlite &&
    export DEVOPS_DB_ENGINE=django.db.backends.sqlite3 && 
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
        common.successMsg("The env with IP ${envip} has been started")
    } else {
        echo "It seems the env has not been started properly"
    }    
}


node ("${SLAVE_NODE}") {
    devops_dos_path = '/var/fuel-devops-venv/fuel-devops-venv/bin/dos.py'
    devops_work_dir = '/var/fuel-devops-venv'
    def dt = new Date().getTime()
    def envname = "${params.STACK_NAME}-${dt}"
    
    stage ('Creating environmet') {
        if ("${params.STACK_NAME}" == '') {
            error("ENV_NAME have to be defined")
        }
        echo "${params.STACK_NAME} ${params.TEMPLATE}"
        if ("${params.TEMPLATE}" == 'Single') {
            echo "Single"
            tpl = '/var/fuel-devops-venv/tpl/clound-init-single.yaml'
        } else if ("${params.TEMPLATE}" == 'Multi') {
            echo "Multi"
        }
        try {
            createDevOpsEnv("${devops_dos_path}","${devops_work_dir}","${tpl}","${envname}")
        } catch (err) {
            error("${err}")
//            eraseDevOpsEnv("${params.ENV_NAME}")   
        }
    }
    stage ('Bringing up the environment') {
       try {
           startupDevOpsEnv("${devops_dos_path}","${devops_work_dir}","${envname}")
       } catch (err) {
           error("${params.STACK_NAME} has not been managed to bring up")
       }
    }
    stage ('Getting environment IP') {
       try {
           envip = getDevOpsIP("${devops_dos_path}","${devops_work_dir}","${envname}").trim()
           echo "${envip}"
           currentBuild.description = "${envname} ${envip}"
       } catch (err) {
           error("IP of the env ${envname} can't be got")
       }                
    }
    stage ('Checking whether the env has finished starting') {
        ifEnvIsReady("${envip}")
        if (DEPLOY_OPENSTACK.toBoolean() == true) {
            stage ('Deploying Openstack') {
                build(job: "${JOB_DEP_NAME}", parameters: [
                    [$class: 'StringParameterValue', name: 'SALT_MASTER_URL', value: "http://${envip}:6969"],
                    [$class: 'StringParameterValue', name: 'STACK_INSTALL', value: STACK_INSTALL],
                    [$class: 'StringParameterValue', name: 'STACK_TYPE', value: "physical"],
                    [$class: 'TextParameterValue', name: 'SALT_OVERRIDES', value: SALT_OVERRIDES]
                ])
            }
/*
                    [$class: 'StringParameterValue', name: 'TEST_TEMPEST_IMAGE', value: "sandriichenko/rally_tempest_docker:docker_aio"],
                    [$class: 'StringParameterValue', name: 'TEST_TEMPEST_PATTERN', value: "set=smoke"],
                    [$class: 'StringParameterValue', name: 'TEST_TEMPEST_TARGET', value: "I@salt:master"],
*/            
        }
        if (DESTROY_ENV.toBoolean() == true) {
             stage ('Bringing down ${envname} environmnet') {
                 try {
                     destroyDevOpsEnv("${devops_dos_path}","${envname}")
                 } catch (err) {
                     error("${envname} has not been managed to bring down")
                 }
             }
         }
    }
    
}

