'use strict';
$(document).ready(function() {
    $('select').material_select();
});
angular.module('cApp', [
    'ngRoute',
    'ngStorage',
    'ngSanitize',
    'ui.bootstrap',
    'cApp.recommender',
    'checklist-model'
])
.config(['$routeProvider', '$locationProvider', function($routeProvider, $locationProvider) {
    $routeProvider
        .when('/', {
            templateUrl: '/assets/components/home.html'
        });
        // Enable html5Mode in order to disable hashbanging
        $locationProvider.html5Mode({
            enabled: true,
            requireBase: false
        });
}]);