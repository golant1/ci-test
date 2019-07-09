/**
 * Docker image build pipeline for OpenStack images with help of LOCI
 * LOCI_REPO_URL - url to loci project
 * LOCI_REPO_REFSPEC - refspec for loci project
 * DOCKER_BUILD_ARGS - Custom build args in format 'NAME=value' one per line
 * CREDENTIALS_ID - gerrit credentials id
 * DOCKER_REGISTRY - url to dev docker registry
 * DOCKER_CREDENTIALS - credentials id to use when connecting to docker registry
 * IMAGE_NAME - Docker image name including tag if any ('<name>[:<tag>]')
 * PROJECT - the name of project to built
 * PROJECT_REPO - the git repo containing the OpenStack project the container should contain
 * PROJECT_REFSPEC - refspec for project
 * PROJECT_REPO_MAPPING - Extra projects repos to be built additionally in the image in JSON format
 * with the below format.
 *  {
 *    designate-dashboard:
 *    {
 *      repo: 'https://github.com/openstack/designate-dashboard',
 *      repo_ref: 'stable/queens'
 *    },
 *    heat-dashboard:
 *    {
 *      repo: 'https://github.com/openstack/heat-dashboard',
 *      repo_ref: 'stable/queens'
 *    }
 *  }
 *
 *
 * EXTRA_FILES - Text with yaml to create files from it in format:
 *  etc/sources.list.d/99extra.list:
 *    content: |
 *      deb [trusted=yes] http://mirantis.repo.url
 *  etc/trusted.gpg.d/extra.gpg:
 *    content: |
 *      dsdjefjejfejj==
 *    encoding: Base64
**/
def gerrit = new com.mirantis.mk.Gerrit()
def git = new com.mirantis.mk.Git()
def common = new com.mirantis.mk.Common()
def python = new com.mirantis.mk.Python()

def builtImage
def fullImageName
def projectExtraRepo
def projectRepoMapping
def componentSha=''
def extraFiles

currentBuild.description = ''
imageDetails = [(PROJECT):[:]]

@NonCPS
def checkoutGitRepositoryRefspec(project, project_repo, credentials, project_refspec, ref = 'master') {
  def git = new com.mirantis.mk.Git()

  if (project_refspec.startsWith('ref')){
    git.checkoutGitRepository(project, project_repo, 'master', credentials)
    dir(project) {
      checkout([
        $class: 'GitSCM',
        branches: [
          [name: 'FETCH_HEAD'],
        ],
        userRemoteConfigs: [
          [url: project_repo, refspec: project_ref, credentialsId: credentials],
        ],
      ])
    }
  } else {
    git.checkoutGitRepository(project, project_repo, project_refspec, credentials)
  }
}


def shouldNOTBePresentCommon = ['aodh', 'barbican', 'barbican-tempest-plugin', 'cinder', 'designate', 'designate-tempest-plugin',
                                'glance', 'glance_store', 'gnocchi', 'heat', 'heat-dashboard', 'heat-tempest-plugin', 'horizon',
                                'horizon-contrail-panels', 'horizon-mirantis-theme', 'ironic', 'ironic-tempest-plugin', 'keystone',
                                'manila', 'manila-tempest-plugin', 'manila-ui', 'mirascan', 'networking-bagpipe',
                                'networking-baremetal', 'networking-bgpvpn', 'networking-generic-switch', 'networking-l2gw',
                                'networking-odl', 'networking-ovn', 'neutron', 'neutron-fwaas', 'neutron-lbaas', 'neutron-vpnaas',
                                'nova', 'octavia', 'octavia-dashboard', 'octavia-tempest-plugin', 'panko', 'python-brick-cinderclient-ext',
                                'python-gnocchiclient', 'python-pankoclient', 'telemetry-tempest-plugin', 'tempest-horizon',
                                'vmware-nsx', 'vmware-nsxlib', 'requirements', 'patrole', 'cinder-tempest-plugin',
                                'cisbench', 'containerd', 'gerrit', 'influxdb-relay', 'jenkins', 'jmx-exporter', 'tempest', 'keystone-tempest-plugin',
                                'libnetwork', 'libvirt-exporter', 'memcached', 'neutron-tempest-plugin', 'postgresql', 'prometheus-es-exporter',
                                'prometheus-relay', 'rally', 'reclass', 'runc', 'salt-pepper', 'sf-notifier', 'telegraf', 'tini']

def shouldNOTBePresentBranch = ['mcp/pike': ['ceilometer'],
                                'mcp/queens': ['django_openstack_auth', 'ceilometer'],
                                'mcp/rocky': ['django_openstack_auth'],
                                'mcp/stein': ['django_openstack_auth'],
                                'master': ['django_openstack_auth'],
                               ]

def customNameMapping = ['keystoneauth1' : 'keystoneauth',
                         'aodhclient': 'python-aodhclient',
                         'django-openstack-auth': 'django_openstack_auth']

//def customNameMappingReverse = customNameMapping.collectEntries { e -> [(e.value): e.key] }

def defaultRepoUrl = PROJECT_REPO.replace("/${PROJECT}",'')

def projects_repo_mapping = """{'horizon': {'rocky': {'octavia-dashboard': {'repo': '${defaultRepoUrl}/octavia-dashboard', 'repo_ref': 'mcp/xenial/rocky'},
                                                      'heat-dashboard': {'repo': '${defaultRepoUrl}/heat-dashboard','repo_ref': 'mcp/xenial/rocky'},
                                                     },
                                            'stein': {'octavia-dashboard': {'repo': '${defaultRepoUrl}/octavia-dashboard', 'repo_ref': 'mcp/stein'},
                                                      'heat-dashboard': {'repo': '${defaultRepoUrl}/heat-dashboard','repo_ref': 'mcp/stein'},
                                                     },
                                            'queens': {'octavia-dashboard': {'repo': '${defaultRepoUrl}/octavia-dashboard', 'repo_ref': 'mcp/queens'},
                                                       'heat-dashboard': {'repo': '${defaultRepoUrl}/heat-dashboard','repo_ref': 'mcp/queens'},
                                                      },
                                           },
                               }"""

if (common.validInputParam('PROJECT_REPO_MAPPING')) {
  projectRepoMapping = readYaml text: PROJECT_REPO_MAPPING
} else {
  def project_repo_mapping_ = readYaml text: projects_repo_mapping
  for (project_mapping in project_repo_mapping_.keySet()){
    if (project_mapping == PROJECT) {
      for (project_branch in project_repo_mapping_[project_mapping].keySet()) {
        if (project_branch == PROJECT_REFSPEC.tokenize('/')[-1]) {
          projectRepoMapping = project_repo_mapping_[project_mapping].get(project_branch)
          common.infoMsg("projectRepoMapping: ${projectRepoMapping}")
        }
      }
    }
  }
}

if (common.validInputParam('EXTRA_FILES')) {
  extraFiles = readYaml text: EXTRA_FILES
}

def GERRIT_HOST = env.GERRIT_HOST ?: 'gerrit.mcp.mirantis.com'

/**
 * Return list of downstreammed OpenStack libraries.
 *
 * @param gerritHost      Gerrir host
 * @param credentialsId   Credentials to connect to gerrit
 * @param branch          Branch to clone project from
 * @param gerritPort      Port number to connect to gerrit
 * @param nameSpace       Namespace with OpenStack libraries
 * return                 List of downstreammed OpenStack libraries in specified namespace.
**/
def getOpenStackDownstreamLibraries(gerritHost, credentialsId, branch, gerritPort='29418', nameSpace='packaging/sources'){
    def ssh = new com.mirantis.mk.Ssh()
    def common = new com.mirantis.mk.Common()

    def projects = []
    def creds = common.getCredentialsById(credentialsId)

    ssh.prepareSshAgentKey(credentialsId)
    ssh.ensureKnownHosts(gerritHost)
    def cmd = "ssh -p ${gerritPort} ${creds.username}@${gerritHost} gerrit ls-projects --show-branch ${branch} |grep ${nameSpace}| awk '{gsub(\"${nameSpace}/\", \"\");print \$2}'"
    projects = ssh.agentSh(cmd).tokenize('\n')

    return projects
}


/**
 * Update upper-constraints.txt to install downstreammed libraries. Two files are returned.
 *   ['uc'] - upper-constraints.txt, patched file with downstream libraries installed from local folders
 *   ['dev_uc'] - dev-upper-constraints.txt, patched file with downstream libraries pinned to dev versions (that were installed according to upper-constraints.txt)
 *
 * @param ucFile             The upper-constraints.txt file path.
 * @param downstreamProjects List of downstreammed projects.
 * @param customNameMapping  Transition name map between gerrit project and python library name.
 * @param libsBasePath       Base path for directory with libraries.
 * @param shouldBeIgnored    Libraries that should be ignored and are good to be not changed (include projects, tempest plugins, etc)
 * return                    Map with modified upper-constraints.txt and dev-upper-constraints.txt
 * raises                    error when found not tracked local library.
**/
def applyDownstreamLibrariesUpperConstraints(ucFile, downstreamProjects, customNameMapping, libsBasePath, shouldBeIgnored, libDevVersions){

    def ucRaw = readFile ucFile
    def ucList = ucRaw.tokenize('\n')
    def resUC = ''
    def resDevUC = ''
    def reqItem
    def reqName
    def reqVersion
    def reqMeta
    def intersectLibs = []

    for (item in ucList){
      reqItem = item.tokenize(';')
      reqName = reqItem[0].tokenize('===')[0]
      reqVersion = reqItem[0].tokenize('===')[1]
      reqMeta = ''

      if (reqName == 'tempest'){
          continue
      }
      if (reqItem.size() > 1) {
          reqMeta = reqItem[1]
      }
      if (reqName in downstreamProjects){
          intersectLibs.add(reqName)
          resUC += "file://${libsBasePath}/${reqName}#egg=${reqName}"
          resDevUC += "${reqName}===${libDevVersions[reqName]}"
      } else if (reqName in customNameMapping.keySet()) {
          intersectLibs.add(reqName)
          resUC += "file://${libsBasePath}/${customNameMapping[reqName]}#egg=${reqName}"
          resDevUC += "${reqName}===${libDevVersions[reqName]}"
      } else {
          resUC += "${item}"
          resDevUC += "${item}"
      }
      resUC += '\n'
      resDevUC += '\n'
    }

    // Check we don't have new dependencies that are not missed
    def missed = downstreamProjects - shouldBeIgnored - intersectLibs - customNameMapping.values()
    if (missed) {
        error("Dependencies: ${missed} are present in downstream and not found in upper-constraints.txt")
    }
    return ['uc': resUC, 'dev_uc': resDevUC]
}

node('docker') {

    def dockerArgs = DOCKER_BUILD_ARGS.trim().readLines().collect { "--build-arg '${it}'" } +
                                ["--build-arg PROJECT=${PROJECT}"] +
                                ['--force-rm',
                                 '--no-cache',
                                 '--pull',
                                 '.',]
    def upperConstraintsPatched
    def upperConstraintsDevPatched
    fullImageName = "${DOCKER_REGISTRY}/${IMAGE_NAME}"
    def workspace = common.getWorkspace()
    def libsBasePath="${workspace}/data"
    def ucFile = "${libsBasePath}/requirements/upper-constraints.txt"
    def ucDevFile = "${libsBasePath}/requirements/dev-upper-constraints.txt"
    def artifacts_dir = '_artifacts/'
    def libDevVersions = [:]


    try {
        sh "mkdir -p ${artifacts_dir}"

        stage('Checkout loci') {
          gerrit.gerritPatchsetCheckout(LOCI_REPO_URL, LOCI_REPO_REFSPEC, 'HEAD', CREDENTIALS_ID)
        }

        stage('Checkout component') {

//          checkoutGitRepositoryRefspec("${libsBasePath}/${PROJECT}", PROJECT_REPO, CREDENTIALS_ID, PROJECT_REFSPEC)
          git.checkoutGitRepository("${libsBasePath}/${PROJECT}", PROJECT_REPO, PROJECT_REFSPEC, CREDENTIALS_ID)

          //Retrieve component sha
          dir("${libsBasePath}/${PROJECT}") {
            componentSha = git.getGitCommit()
            currentBuild.description += "component_sha:" + componentSha + ';'
          }

          //Prepare image details
          imageDetails[PROJECT]["tag"] = IMAGE_NAME.tokenize(":")[-1]
          imageDetails[PROJECT]["url"] = "${DOCKER_REGISTRY}/${IMAGE_NAME}"
          imageDetails[PROJECT]["origin"] = componentSha

          writeYaml file: "${artifacts_dir}/image-metadata.yml", data: imageDetails

          if (PROJECT == "requirements"){
            def projects = getOpenStackDownstreamLibraries(GERRIT_HOST, CREDENTIALS_ID, PROJECT_REFSPEC)
            def repoBaseUrl = PROJECT_REPO.replace('/requirements','')
            def shouldBeIgnored = shouldNOTBePresentCommon
            if (shouldNOTBePresentBranch.containsKey(PROJECT_REFSPEC)){
              shouldBeIgnored = shouldBeIgnored + shouldNOTBePresentBranch[PROJECT_REFSPEC]
            }

            def venv = "${workspace}/venv"
            python.setupVirtualenv("${workspace}/venv", 'python2', ['setuptools'])

            for (pr in projects - shouldBeIgnored) {
                git.checkoutGitRepository("${libsBasePath}/${pr}", "${repoBaseUrl}/$pr", PROJECT_REFSPEC, CREDENTIALS_ID)
                def lname
                def lversion
                dir("${libsBasePath}/${pr}") {
                    lname = python.runVirtualenvCommand(venv, 'echo "$(python setup.py --name)"').trim()
                    // os-vif repors os_vif here, but in upper-constraints and requirements os-vif is used
                    if (lname == 'os_vif'){
                      lname = 'os-vif'
                    }
                    lversion = python.runVirtualenvCommand(venv, 'echo "$(python setup.py --version)"').trim()
                }
                libDevVersions[lname] = lversion
            }

            // Use /tmp as base path as libraries will be placed to that container directory
            def ucs = applyDownstreamLibrariesUpperConstraints(ucFile, projects, customNameMapping, '/tmp', shouldBeIgnored, libDevVersions)
            upperConstraintsPatched = ucs['uc']
            upperConstraintsDevPatched = ucs['dev_uc']
            writeFile file: ucFile, text: upperConstraintsPatched
            writeFile file: "${artifacts_dir}/upper-constraints.txt", text: upperConstraintsPatched
            writeFile file: ucDevFile, text: upperConstraintsDevPatched
            writeFile file: "${artifacts_dir}/dev-upper-constraints.txt", text: upperConstraintsDevPatched
          }

          if (projectRepoMapping) {
            def extra_docker_args = []
            for (extra_project in projectRepoMapping.keySet()){

              extra_docker_args.add("/tmp/${extra_project}")

              def extra_project_repo = projectRepoMapping[extra_project].get('repo')
              def extra_project_ref = projectRepoMapping[extra_project].get('repo_ref')

              common.infoMsg("Cloning extra project: ${extra_project} with refspec: ${extra_project_ref}")

//              checkoutGitRepositoryRefspec("${libsBasePath}/${extra_project}", extra_project_repo, CREDENTIALS_ID, extra_project_ref)
              git.checkoutGitRepository("${libsBasePath}/${PROJECT}", extra_project_repo, extra_project_ref, CREDENTIALS_ID)
            }
            dockerArgs.add(2,"--build-arg EXTRA_PROJECTS='${extra_docker_args.join(' ')}'")
          }

        }

        stage('Add extra files from EXTRA_FILES param') {
          if (extraFiles) {
            for (f in extraFiles.keySet()){
              content = extraFiles[f]['content']
              encoding = extraFiles[f]['encoding']
              if (encoding) {
                writeFile file: f, text: content, encoding: encoding
              } else {
                writeFile file: f, text: content
              }
            }
          }
        }

        stage('Build Image') {
          // NOTE(vsaienko) unless issue https://issues.jenkins-ci.org/browse/JENKINS-46105 is fixed build image
          // with shell command.
          sh("docker build -t ${fullImageName}  ${dockerArgs.join(' ')}")
          builtImage = docker.image("${fullImageName}")
          currentBuild.description += "image_name:" + fullImageName + ';'
        }

        stage('Push Image') {
          def imageNameList = fullImageName.tokenize(':')
          def imageName = fullImageName.tokenize(':')[0]
          def imageTag = 'nightly'
          if (imageNameList.size() > 1 ){
            imageTag = fullImageName.tokenize(':')[1]
          }
          docker.withRegistry("https://${DOCKER_REGISTRY}/", DOCKER_CREDENTIALS) {
            builtImage.push(imageTag)
          }
        }

    } catch (e) {
       currentBuild.result = 'FAILURE'
       throw e
    } finally {
        stage('Archive artifacts'){
            archiveArtifacts allowEmptyArchive: true, artifacts: "${artifacts_dir}/*", excludes: null
        }
        stage('Cleanup') {
            deleteDir()
        }
    }
}

