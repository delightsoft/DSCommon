module = angular.module 'docflow.widget.photo.control', ['docflow.config' ,'ui.bootstrap.modal']

module.directive 'docflowWidgetPhotoControl',[
  '$docflowUtils', '$modal', '$http'
  (($docflowUtils, $modal, $http)->
    return {
    restrict: 'A'
    require: 'ngModel'
    link: (($scope, element, attrs, model) ->

      ($scope.changePhoto =->

        photoSelect = $modal.open(
          templateUrl: "/tmpl/modal/form-photo-control"
          backdrop: 'static'
          controller: [
            '$scope',
            (($modalScope) ->

              $modalScope.item =
                  url: model.$viewValue

              $modalScope.ok = (->
                photoSelect.close($modalScope.item.url)
                return)

              $modalScope.cancel = (->
                photoSelect.dismiss('cancel')
                return)

              $modalScope.upload =(files)->
                fd = new FormData(@)
                fd.append('file', files[0])
                fd.append('id', $scope.editor.docId)
                url = '/uploadImage'

                $http.post(url, fd,
                  withCredentials: true,
                  headers: {'Content-Type': undefined },
                  transformRequest: angular.identity
                ).success((data, status, headers, config)->
                  photoSelect.close(data.filelink)
                ).error(-> console.error 'Upload fail')
                return


              return)
          ])

        photoSelect.result.then ((url)->
          model.$setViewValue(url)
          return)

        return)
      return)
    })]

