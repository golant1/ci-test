/**
 *
 *
 *
 **/

http = new com.mirantis.mk.Http()
data = [:]

def listPublish(server) {

    println (http.restGet(server, '/api/publish'))

}

def snapshotCreate(server, repo) {
    def now = new Date();
    def ts = now.format("yyyyMMddHHmmss", TimeZone.getTimeZone('UTC'));
    def snapshot = "${repo}-${ts}-oscc-dev"

    def data = [
        'Name': snapshot
    ]
    resp = http.restPost(server, "/api/repos/${repo}/snapshots", data) 
    echo resp

//    try {
//        sh(script: "curl -f -X POST -H 'Content-Type: application/json' --data '{\"Name\":\"$snapshot\"}' ${server}/api/repos/${repo}/snapshots", returnStdout: true, )
//    } catch (err) {
//        echo (err)
//    }

    return snapshot
}

def snapshotPublish(server, snapshot, distribution, components, prefixes = []) {

    for (prefix in prefixes) {
        sh(script: "curl -X POST -H 'Content-Type: application/json' --data '{\"SourceKind\": \"snapshot\", \"Sources\": [{\"Name\": \"${snapshot}\", \"Component\": \"${components}\"}], \"Architectures\": [\"amd64\"], \"Distribution\": \"${distribution}\"}' ${server}/api/publish/${prefix}", returnStdout: true, )
    }

}

def snapshotUnpublish(server, snapshot, distribution, components, prefixes = []) {

    for (prefix in prefixes) {
        sh(script: "curl -X DELETE http://172.16.48.254:8084/api/publish/${prefix}/${distribution}", returnStdout: true, )
    }

}

node('python'){
    def server = [
        'url': "http://172.18.162.193:8080"
    ]
    def repo = "ubuntu-xenial-salt"
    def distribution = "dev-os-salt-formulas"
    def components = "dev-salt-formulas"
    def prefixes = ["oscc-dev", "s3:aptcdn:oscc-dev"]
    def tmp_repo_node_name = "apt.mirantis.com"
    def deployBuild
    def STACK_RECLASS_ADDRESS = 'https://gerrit.mcp.mirantis.net/salt-models/mcp-virtual-aio'
    def OPENSTACK_RELEASES = 'ocata,pike'
    def buildResult = [:]
    def notToPromote

    stage("Prepare nightly repo for testing"){
        def snapshot = snapshotCreate(server, repo)
        echo (snapshot)
//        echo (snapshotPublish(server, snapshot, distribution, components, prefixes))
    }
   
    stage("Deploying environment and testing"){
/*        for (openstack_release in OPENSTACK_RELEASES.tokenize(',')) {
            deployBuild = build(job: 'oscore-MCP1.1-virtual_mcp11_aio-pike-stable', propagate: false, parameters: [
                [$class: 'StringParameterValue', name: 'EXTRA_REPO', value: "deb [arch=amd64] http://${tmp_repo_node_name}/oscc-dev ${distribution} ${components}"],
                [$class: 'StringParameterValue', name: 'EXTRA_REPO_PRIORITY', value: "1200"],
                [$class: 'StringParameterValue', name: 'EXTRA_REPO_PIN', value: "origin ${tmp_repo_node_name}"],
                [$class: 'BooleanParameterValue', name: 'STACK_DELETE', value: false],
                [$class: 'StringParameterValue', name: 'STACK_RECLASS_ADDRESS', value: STACK_RECLASS_ADDRESS],
                [$class: 'StringParameterValue', name: 'STACK_RECLASS_BRANCH', value: "stable/" + openstack_release.replaceAll(' ','')],
            ]) 
            buildResult[openstack_release.replaceAll(' ','')] = deployBuild.result
        } */
    }

    stage("Managing deployment results") {
        notToPromote = buildResult.find {openstack_release, result -> result != 'SUCCESS'}

        buildResult.each {openstack_release, result -> 
            println("${openstack_release}: ${result}")
        }
    }

    stage("Promotion to testing repo"){
        if (notToPromote) {
            echo "Snapshot can't be promoted!!!"
        }
    }
}
