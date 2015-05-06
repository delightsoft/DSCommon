module = angular.module 'docflow.widget.texteditor', ['docflow.config']

module.directive 'docflowWidgetTexteditor', [
  (() ->
    return {
    restrict: 'A'
    require: 'ngModel'
    link: (($scope, element, attrs, model) ->

      if attrs.docflowWidgetTexteditor
        params = $scope.$eval attrs.docflowWidgetTexteditor

      if(angular.isDefined(params) and params.mode is 'air')
        element.redactor(
          air : true
#          airButtons: ['formatting', 'bold', 'italic', 'deleted']
          deniedTags: ['html', 'head', 'link', 'body', 'meta', 'applet']
          boldTag: 'strong'
          italicTag: 'i'
          changeCallback: (->
            model.$setViewValue element.html()
            if (!$scope.$$phase)
              $scope.$apply()
            return)
        )
      else
        element.redactor(
          minHeight: 350
          autoresize: false
          convertDivs: false
          removeEmptyTags: false
          cleanFontTag: false
          convertLinks: false
          imageUpload: '/uploadImage'
          uploadFields:
            id: (-> $scope.item.id)
          deniedTags: ['html', 'head', 'link', 'body', 'meta', 'applet']
          boldTag: 'strong'
          italicTag: 'i'
          plugins: ['fullscreen']
          changeCallback: (->
            model.$setViewValue element.html()
            if (!$scope.$$phase)
              $scope.$apply()
            return)
        )

      model.$render = (->
        element.html model.$viewValue
        return)
      $scope.$on '$destroy', (->
        try
          element.redactor("destroy")
        catch err
          # Nothing - In case of back-space screen switch jQuery removes data before angular $destroy
        return)
      return)
    }
  )]

