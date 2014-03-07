module = angular.module 'docflow.widget.subtable', ['docflow.config', 'ui.sortable']

module.directive 'docflowWidgetSubtable', [
  '$docflow', '$http', '$timeout', '$log',
  (($docflow, $http, $timeout, $log) ->
    scope: true
    restrict: 'A'
    require: 'ngModel'
    link: (($scope, element, attrs, ngModel) ->

      params = {}

      # params for ui-sortable
      $scope.sortableOptions =
        handle: '.fa-ellipsis-v'
        axis: 'y'

      if attrs.docflowWidgetSubtable
        angular.extend params, $scope.$eval attrs.docflowWidgetSubtable

      if !params.field
        $log.error 'Missing \'field\' attribute'
        return

      $scope.$watch '$parent.item', ((item)->
        if !item.$n?[params.field] # it's not editable
          $scope.list = item[params.field]
        else
          newLinePrototype = item.$n[params.field]
          model = item[params.field]
          required = angular.isDefined attrs.required
          $scope.list = list = (item for item in model)
          $scope.newline = newline = angular.copy newLinePrototype
          list.push newline
          $scope.$watch 'newline', ((n) ->
            if newline
              newline = null
            else
              model.push $scope.newline
              newline = $scope.newline = angular.copy newLinePrototype
              list.push newline
            return), true
          $scope.remove = (item) ->
            ind = model.indexOf item
            model.splice ind, 1
            list.splice ind, 1
          modelUpdated = ((newModel) ->
            $scope.list = list = angular.copy newModel
            list.push $scope.newline
            model = newModel
            return)
          $scope.$parent.$watch "item.#{params.field}", ((fld) ->
            if required
              ngModel.$setValidity 'required', fld.length != 0
            if fld.length + 1 != list.length
              modelUpdated fld
              return
            for e, i in fld
              if e != list[i]
                modelUpdated fld
                return
            return), true
        return)
      return)
  )]
