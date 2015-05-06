(function() {
  var module;

  module = angular.module('docflow.widget.photoСontrol', ['docflow.config', 'ui.bootstrap.modal']);

  module.directive('docflowWidgetPhotoControl', [
    '$docflowUtils', '$modal', '$http', (function($docflowUtils, $modal, $http) {
      return {
        restrict: 'A',
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          $scope.changePhoto = function() {
            var photoSelect;
            photoSelect = $modal.open({
              templateUrl: "/tmpl/ngApp/modal/form-photo-control",
              backdrop: 'static',
              controller: [
                '$scope', (function($modalScope) {
                  $modalScope.item = {
                    url: model.$viewValue
                  };
                  $modalScope.ok = (function() {
                    photoSelect.close($modalScope.item.url);
                  });
                  $modalScope.cancel = (function() {
                    photoSelect.dismiss('cancel');
                  });
                  $modalScope.upload = function(files) {
                    var fd, url;
                    fd = new FormData(this);
                    fd.append('file', files[0]);
                    fd.append('id', $scope.editor.docId);
                    url = '/uploadImage';
                    $http.post(url, fd, {
                      withCredentials: true,
                      headers: {
                        'Content-Type': void 0
                      },
                      transformRequest: angular.identity
                    }).success(function(data, status, headers, config) {
                      return photoSelect.close(data.filelink);
                    }).error(function() {
                      return console.error('Upload fail');
                    });
                  };
                })
              ]
            });
            photoSelect.result.then((function(url) {
              model.$setViewValue(url);
            }));
          };
        })
      };
    })
  ]);

}).call(this);
