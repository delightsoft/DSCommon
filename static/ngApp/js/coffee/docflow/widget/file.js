(function() {
  var module;

  module = angular.module('docflow.widget.file', ['angularFileUpload', 'docflow.config']);

  module.directive('docflowWidgetFile', [
    '$fileUploader', '$timeout', '$rootScope', '$log', (function($fileUploader, $timeout, $rootScope, $log) {
      return {
        restrict: 'A',
        require: 'ngModel',
        scope: true,
        link: (function($scope, element, attrs, model) {
          var $inputFile, options, params;
          options = {
            scope: $scope,
            removeAfterUpload: true
          };
          if (angular.isObject(params = $scope.$eval(attrs.docflowWidgetFile))) {
            angular.extend(options, params);
          }
          $inputFile = $('input[type="file"]', element);
          $scope.uploader = $fileUploader.create(options);
          $scope.progress = null;
          $scope.selectFile = (function() {
            setTimeout((function() {
              $inputFile.click();
            }), 0);
          });
          $scope.clearFile = (function() {
            model.$setViewValue(null);
          });
          $scope.uploader.bind('progress', (function(item, progress) {
            $scope.progress = "" + progress.progress + "%";
            $rootScope.$digest();
          }));
          $scope.uploader.bind('success', (function(event, xhr, item, response) {
            $scope.progress = "100%";
            $timeout((function() {
              if ($scope.editor) {
                $scope.editor.upload--;
              }
              $scope.progress = null;
            }), 1500);
            $rootScope.$digest();
          }));
          $scope.uploader.bind('error', (function(event, xhr, item, response) {
            $scope.progress = "0%";
            $timeout((function() {
              if ($scope.editor) {
                $scope.editor.upload--;
              }
              $scope.progress = null;
            }), 1500);
            $rootScope.$digest();
          }));
          $scope.uploader.bind('complete', (function(event, xhr, item, response) {
            if (response.code === 'Ok') {
              model.$setViewValue(response.result);
              $rootScope.$digest();
            }
          }));
          $scope.uploader.bind('afteraddingfile', (function(event, item) {
            model.$setViewValue({
              id: null,
              text: item.file.name,
              blocked: false
            });
            $scope.progress = "0%";
            if ($scope.editor) {
              $scope.editor.upload++;
            }
            $rootScope.$digest();
            $scope.uploader.uploadAll();
          }));
        })
      };
    })
  ]);

}).call(this);
