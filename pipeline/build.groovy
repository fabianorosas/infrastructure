#!groovy

loader = load 'infrastructure/pipeline/lib/loader.groovy'
pipelineStages = loader.load 'build/stages.groovy'

def execute() {
  timestamps {
    stage('Initialize') {
      pipelineStages.initialize()
    }

    stage('Authorize') {
      pipelineStages.authorize()
    }

    stage('Validate') {
      node('validation_slave_label') {
        pipelineStages.validate()
      }
    }

    catchError {
      stage('Build packages') {
        node('builds_slave_label') {
          pipelineStages.buildPackages()
        }
      }
    }

    catchError {
      stage('Build ISO') {
        node('builds_slave_label') {
          pipelineStages.buildIso()
        }
      }
    }

    stage('Upload') {
      node('builds_slave_label') {
        pipelineStages.uploadArtifacts()
      }
    }
  }
}

return this
