def common = new com.mirantis.mk.Common()
def aptly = new com.mirantis.mk.Aptly()

node() {
  try{
    stage("promote") {
      lock("aptly-api") {
        aptly.promotePublish('http://172.16.48.254:8084', 'xenial/nightly', 'oscc-dev/xenial', 'false', 'salt', '', '', '-d --timeout 600', '')
        aptly.promotePublish('http://172.16.48.254:8084', 's3:aptcdn:xenial/nightly', 's3:aptcdn:oscc-dev/xenial', 'false', 'salt', '', '', '-d --timeout 600', '')
      }
    }
  } catch (Throwable e) {
     // If there was an error or exception thrown, the build failed
     currentBuild.result = "FAILURE"
     currentBuild.description = currentBuild.description ? e.message + " " + currentBuild.description : e.message
     throw e
  } finally {
        common.infoMsg('FINAL')
//     common.sendNotification(currentBuild.result,"",["slack"])
  }
}
