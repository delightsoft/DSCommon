module = angular.module 'docflow.widget.file', [

  'angularFileUpload'

  'docflow.config'
]

module.directive 'docflowWidgetFile',
  ['$fileUploader', '$timeout', '$rootScope', '$log',
  (($fileUploader, $timeout, $rootScope, $log) ->
    restrict: 'A'
    require: 'ngModel'
    scope: true
    link: (($scope, element, attrs, model) ->

      options =
        scope: $scope
        removeAfterUpload: true
      if angular.isObject(params = $scope.$eval attrs.docflowWidgetFile)
        angular.extend options, params

      $inputFile = $('input[type="file"]', element)

      $scope.uploader = $fileUploader.create options
      $scope.progress = null
      $scope.selectFile = (->
        # Note: setTimeout() (not $timeout) is used to break up possible $digest() inclose, that has to happent on file in dialog
        setTimeout (->
          $inputFile.click()
          return), 0
        return)
      $scope.clearFile = (->
        model.$setViewValue null
        return)

      $scope.uploader.bind 'progress', ((item, progress) ->
#        $log.info "$fileUploader.on 'progress'", item, progress
        $scope.progress = "#{progress.progress}%"
        $rootScope.$digest()
        return)

      $scope.uploader.bind 'success', ((event, xhr, item, response) ->
#        $log.info "$fileUploader.on 'success'", event, xhr, item, response
        $scope.progress = "100%"
        $timeout (->
          $scope.editor.upload-- if $scope.editor
          $scope.progress = null
          return), 1500
        $rootScope.$digest()
        return)

      $scope.uploader.bind 'error', ((event, xhr, item, response) ->
#        $log.info "$fileUploader.on 'error'", event, xhr, item, response
        $scope.progress = "0%"
        $timeout (->
          $scope.editor.upload-- if $scope.editor
          $scope.progress = null
          return), 1500
        $rootScope.$digest()
        return)

      $scope.uploader.bind 'complete', ((event, xhr, item, response) ->
#        $log.info "$fileUploader.on 'complete'", event, xhr, item, response
        if response.code == 'Ok'
          model.$setViewValue response.result
          $rootScope.$digest()
        return)

#      $scope.uploader.bind 'cancel', ((event, xhr, item) ->
##        $log.info "$fileUploader.on 'cancel'", event, xhr, item
#        return)
#
      $scope.uploader.bind 'afteraddingfile', ((event, item) ->
#        $log.info "element.on 'afteraddingfile'", event, item
        model.$setViewValue
          id: null
          text: item.file.name
          blocked: false
        $scope.progress = "0%"
        $scope.editor.upload++ if $scope.editor
        $rootScope.$digest()
        $scope.uploader.uploadAll()
        return)

# TODO: This gets a call then file is zero length. Would be right to inform user
#      $scope.uploader.bind 'whenaddingfilefailed', ((event, item) ->
#        $log.info "element.on 'whenaddingfilefailed'", event, item
#        message = $docflowConfig.messages['emptyFile']?.replace '{{file}}', file
#        return)

#      $scope.$on 'beforeupload', ((event, item) ->
#        $log.info "$scope.$on 'beforeupload'", event, item
#        return)
#
#      $scope.$on 'in:progress', ((event, item, progress) ->
#        $log.info "$scope.$on 'in:progress'", event, item, progress
#        return)

      return))]
