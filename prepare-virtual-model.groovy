common = new com.mirantis.mk.Common()
git = new com.mirantis.mk.Git()
openstack = new com.mirantis.mk.Openstack()
orchestrate = new com.mirantis.mk.Orchestrate()
python = new com.mirantis.mk.Python()
salt = new com.mirantis.mk.Salt()
test = new com.mirantis.mk.Test()

def slave_node = 'python'

if (common.validInputParam('SLAVE_NODE')) {
    slave_node = SLAVE_NODE
}

node(slave_node) {

        venvPepper = "${workspace}/venvPepper"
        python.setupPepperVirtualenv(venvPepper, SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)

        
        stage("Adjust reclass classes in control nodes to run tests") {
            minions = salt.getMinions(venvPepper, 'ctl*')
            for (minion in minions) {
                common.infoMsg("${minion}")
                def saltMasterTarget = ['expression': 'I@salt:master', 'type': 'compound']
                result = salt.runSaltCommand(venvPepper, 'local', saltMasterTarget, 'reclass.node_update', null, null, ["name": "${minion}", "classes": ["system.cinder.control.backend.lvm","system.cinder.volume.single","system.cinder.volume.backend.lvm","system.linux.storage.loopback"], "parameters": ["loopback_device_size": 20]])
                salt.checkResult(result)
            }

        }

        stage("Adjust reclass classes in gateway nodes to run tests") {
            minions = salt.getMinions(venvPepper, 'gtw*')
            for (minion in minions) {
                common.infoMsg("${minion}")
                def saltMasterTarget = ['expression': 'I@salt:master', 'type': 'compound']
                result = salt.runSaltCommand(venvPepper, 'local', saltMasterTarget, 'reclass.node_update', null, null, ["name": "${minion}", "classes": ["service.runtest.tempest.linux"]])
                salt.checkResult(result)
            }

        }

        stage("Apply changes on the nodes") {
            salt.fullRefresh(venvPepper, "*")            
            salt.enforceState(venvPepper, 'cfg*', 'linux.storage')
            salt.enforceState(venvPepper, 'cfg*', 'glance.client')
            salt.enforceState(venvPepper, 'cfg*', 'linux.network')
            salt.enforceState(venvPepper, 'cfg*', 'nova.client')
        }


}
