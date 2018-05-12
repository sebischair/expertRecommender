'use strict';

const SPARK_TEAM = {name: 'Spark Team', members: [{name: 'Kay Ousterhout'}, {name: 'Michael Armbrust'}]};
const HADOOP_TEAM = {name: 'Hadoop Team', members: [{name: 'Raghu Angadi'}, {name: 'Robert Chansler'}]};
const MAX_MUSTERMANN = {name: "Max Mustermann", role: 'Product Owner', time: 'true', openIssues: 0, rank: -1};

var recommenderApp = angular.module('cApp.recommender', ['ngRoute', 'ngSanitize']);

recommenderApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider
        .when('/recommender/issue', {
            templateUrl: '/assets/components/recommender/issue.html',
            controller: 'RecommenderCtrl'
        });
}]);

recommenderApp.directive('onFinishRender', function ($timeout) {
    return {
        restrict: 'A',
        link: function (scope, element, attr) {
            if (scope.$last === true) {
                $timeout(function () {
                    scope.$emit(attr.onFinishRender);
                    Materialize.updateTextFields();
                });
            }
        }
    }
});

recommenderApp.controller('RecommenderCtrl', ['$scope', '$http', '$location', function ($scope, $http, $location) {

    /* Default values and variables */
    /*############################################*/
    $scope.settings = {
        algorithms: [
            {id: 1, name: 'Vanish', selected: true},
            {id: 2, name: 'LDA', selected: false},
            {id: 3, name: 'LSA', selected: false},
            {id: 4, name: 'KNN', selected: false},
            {id: 5, name: 'Concept', selected: false}],
        datasets: [
            {id: 1, name: 'all', selected: true},
            {id: 2, name: 'DD', selected: false}],
        projectKeys: [
            {id: 1, name: 'SPARK', selected: true},
            {id: 2, name: 'HADOOP', selected: false}],
        priorities: [
            {id: 1, name: 'Blocker'},
            {id: 2, name: 'Critical'},
            {id: 3, name: 'Major'},
            {id: 2, name: 'Minor'}],
        types: [
            {id: 1, name: 'Epic'},
            {id: 2, name: 'New Feature'},
            {id: 3, name: 'User Story'},
            {id: 4, name: 'Bug'}],
        status: [
            {id: 1, name: 'Open'},
            {id: 2, name: 'Reopened'},
            {id: 3, name: 'In Progress'},
            {id: 4, name: 'Resolved'},
            {id: 5, name: 'Closed'},]
    };

    var self = this;
    self.projectKey = $scope.settings.projectKeys.find(pK => pK.selected).name.toUpperCase();
    $scope.algorithm = $scope.settings.algorithms.find(a => a.selected).name.toLowerCase();
    self.dataset = $scope.settings.datasets.find(d => d.selected).name.toLowerCase();
    self.assignees = [];
    $scope.comments = [];
    $scope.issueEdited = false;
    $scope.title = "";
    $scope.text = "";
    $scope.commentText = "";
    $scope.issueCreated = false;
    $scope.issueEvaluated = false;
    $scope.assignedExperts = [];
    $scope.recommendedExperts = [];
    $scope.assignedNovices = [];
    $scope.recommendedNovices = [];
    $scope.explanationRecommendation = [];
    $scope.responsible = "";
    $scope.selectedStatus = "";
    $scope.selectedType = "";
    $scope.selectedPriority = "";
    $scope.numberExperts = 0;
    $scope.numberNovices = 0;
    $scope.teams = [SPARK_TEAM];
    $scope.teamEdit = '';
    $scope.expertButton = false;
    $scope.noviceButton = false;

    /* Init functions */
    /*############################################*/
    angular.element(document).ready(function () {
        $('select').material_select();
        $('.modal').modal({
            ready: function(modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.
                $('.collapsible').collapsible();
            }
        });
        $('.collapsible').collapsible();
    });

    $scope.initAutocomplete = function () {
        $('input.autocomplete').autocomplete({
            data: self.assignees,
            limit: 20, // The max amount of results that can be shown at once. Default: Infinity.
            onAutocomplete: function (val) {
                if ($scope.teamEdit.members.findIndex(m => m.name === val) === -1) {
                    self.addMember(val);
                    $scope.$apply();
                } else {
                    Materialize.toast('Expert already in team', 5000);
                }
            },
            minLength: 1, // The minimum length of the input for the autocomplete to start. Default: 1.
        });
    };

    $scope.initTooltip = function () {
        $('.tooltipped').tooltip({delay: 50, html: true});
    };

    $scope.initDropdown = function () {
        $('.dropdown-button').dropdown({constrainWidth: false, gutter: 40, belowOrigin: true, alignment: 'right'});
        $(".disabled-dropdown-item").click(function(event) {
            event.stopPropagation();
        });
        $(document.body).click(function(e){
            if (e.target.id !== "expertButton") {
                if ($scope.expertButton) {
                    $scope.toggleButton('expert');
                    $scope.$apply();
                }
            }
            if (e.target.id !== "noviceButton") {
                if ($scope.noviceButton) {
                    $scope.toggleButton('novice');
                    $scope.$apply();
                }

            }
        });
    };

    $scope.$on('ngRepeatFinished', function (ngRepeatFinishedEvent) {
        $scope.initTooltip();
    });

    /* Helper print functions */
    /*############################################*/
    $scope.printMembers = function (members) {
        return "Members: " + Array.prototype.map.call(members, member => member.name).join(', ');
    };

    $scope.countExperts = function() {
        if (-1 === $scope.assignedExperts.findIndex(e => e.name === MAX_MUSTERMANN.name)) {
            return $scope.assignedExperts.length;
        } else {
            return $scope.assignedExperts.length - 1;
        }
    }

    $scope.printCommaSeparated = function (conceptsAmount) {
        var conceptsAmountString = [];
        conceptsAmount.forEach(cA => {
            Object.keys(cA).forEach(key => {
                conceptsAmountString.push(key + ' (' + cA[key] + ')');
            });
        });

        return Array.prototype.map.call(conceptsAmountString, s => s).join(', ');
    };

    $scope.printSkills = function (skillsRank, br) {
        var skillsString = [];
        skillsRank.forEach(skill => {
            Object.keys(skill).forEach(key => {
                var hash = $scope.algorithm === 'knn' ? ' ' : ' #';
                if (skill[key] > -1) {
                    skillsString.push(key + hash + skill[key]);
                } else {
                    skillsString.push(key + " n.a.");
                }
            });
        });
        var i = 0;
        var textSkills = Array.prototype.map.call(skillsString, s => s).join(', ');
        var finalText = "";
        if (br) {
            textSkills.split("").forEach(character => {
                finalText += character;
                if (character === ",") {
                    i++;
                    if (i % 3 === 0) {
                        finalText += "<br>";
                    }
                }
            });
        } else {
            finalText = textSkills
        }

        return finalText;
    };

    /* Click functions settings & status */
    /*############################################*/
    $scope.onSelectChange = function (type) {
        if ($scope.selectedPriority !== null && $scope.selectedType !== null) {
            var nE = 0;
            var nN = 0;
            if ($scope.selectedType == "Epic") {
                nE = 3;
                nN = 2;
            }
            if ($scope.selectedType === "User Story" || $scope.selectedType === "New Feature") {
                nE = 2;
                nN = 1;
            }
            if ($scope.selectedType === "Bug") {
                nE = 1;
                nN = 1;
            }

            if (nE > 0) {
                $scope.issueEvaluated = true;
            }

            $scope.numberExperts = nE;
            $scope.numberNovices = nN;
        }
    };

    $scope.onSelectSettings = function (name, type) {
        if (type === "dataset") {
            self.dataset = name.toLowerCase();
            console.log(self.dataset);
        }
        if (type === "algorithm") {
            $scope.algorithm = name.toLowerCase();
            $scope.explanationRecommendation = [];
            console.log($scope.algorithm);
        }
        if (type === "projectKey") {
            self.projectKey = name.toUpperCase();
            console.log(self.projectKey);
            if (self.projectKey === 'SPARK') {
                $scope.teams = [SPARK_TEAM];
            }
            if (self.projectKey === 'HADOOP') {
                $scope.teams = [HADOOP_TEAM];
            }
        }
    };

    /* Add & remove experts */
    /*############################################*/
    $scope.toggleButton = function(type) {
        if (type === "expert") {
            $scope.expertButton = !$scope.expertButton;
        }
        if (type === "novice") {
            $scope.noviceButton = !$scope.noviceButton;
        }
    }

    $scope.remove = function (name, type) {
        var position = 0;
        if (type === 'expert') {
            // back to old position
            position = $scope.assignedExperts.findIndex(e => e.name === name);
            var expert = $scope.assignedExperts[position];
            $scope.recommendedExperts.splice($scope.recommendedExperts.findIndex(e => e.result < expert.result), 0, expert);
            $scope.assignedExperts.splice(position, 1);
        }
        if (type === 'novice') {
            // beginning of list as it is random
            position = $scope.assignedNovices.findIndex(n => n.name === name);
            $scope.recommendedNovices.unshift($scope.assignedNovices[position]);
            $scope.assignedNovices.splice(position, 1);
        }
    };

    $scope.changeResponsible = function (name) {
        $scope.responsible = name;
        Materialize.toast(name + " now responsible", 4000)
    };

    $scope.addAssigneeEvent = function (name, type, e) {
        // dirty to remove tooltip
        $(e.target).trigger('mouseleave');

        $scope.addAssignee(name, type);
    };

    $scope.addAssignee = function (name, type) {
        var position = 0;
        if (type === 'expert' || type === 'help') {
            if (name === MAX_MUSTERMANN.name) {
                $scope.assignedExperts.push(MAX_MUSTERMANN);
            } else {
                position = $scope.recommendedExperts.findIndex(e => e.name === name);
                if (position !== -1) {
                    if (type === 'help') {
                        $scope.recommendedExperts[position].role = 'Evaluate Problem';
                    }
                    $scope.assignedExperts.push($scope.recommendedExperts[position]);
                    $scope.recommendedExperts.splice(position, 1);
                    $scope.closeHelpModal();
                }
            }
        }
        if (type === 'novice') {
            position = $scope.recommendedNovices.findIndex(n => n.name === name);
            if (position !== -1) {
                $scope.assignedNovices.push($scope.recommendedNovices[position]);
                $scope.recommendedNovices.splice(position, 1);
            }
        }
    };

    $scope.closeHelpModal = function () {
        $('#help').modal('close');
    };

    /* Create issue with http Request */
    /*############################################*/
    $scope.createIssue = function () {
        $scope.issueEdited = !$scope.issueEdited;
        // if issue is for first time created
        if (!$scope.issueCreated) {
            $scope.responsible = MAX_MUSTERMANN.name;
            $scope.assignedExperts = [MAX_MUSTERMANN];
            $scope.issueCreated = !$scope.issueCreated;

            // Initialize select elements
            $('#selectStatus').prop("disabled", false);
            $('#selectStatus').find('option[value=' + $scope.settings.status[0].name + ']').prop('selected', true);
            $('#selectPriority').prop("disabled", false);
            $('#selectType').prop("disabled", false);
            $('select').material_select();
        }

        if ($scope.issueEdited && $scope.issueCreated) {
            self.getAssignees();
            self.getExperts();
        }
    };

    self.getAssignees = function () {
        var data = {
            projectKey: self.projectKey
        };
        $http.post("/getAssignees", data)
            .then(function (response) {
                console.log(response);
                self.assignees = response.data;
            });
    };

    self.getExperts = function () {
        Materialize.toast('Getting Experts, Please Wait!', 5000);
        $("#progress").css({
            "visibility": "visible"
        });
        var data = {
            algorithm: $scope.algorithm,
            projectKey: self.projectKey,
            textToClassify: $scope.title + " " + $scope.text,
            alreadyAssignedNovices: $scope.assignedNovices
        };
        $http.post("/getRecommendations", data)
            .then(function (response) {
                console.log(response);
                $scope.recommendedExperts = response.data.expertRecommendation;
                $scope.recommendedNovices = response.data.noviceRecommendation;
                $scope.explanationRecommendation = response.data.explanationRecommendation;
                console.log($scope.explanationRecommendation);
                Materialize.toast('Experts loaded!', 4000);

                // Update existing values
                if ($scope.assignedExperts.length > 0) {
                    $scope.assignedExperts.forEach(p => {
                        $scope.assignedExperts = $scope.assignedExperts.filter(a => a.name !== p.name);
                        $scope.addAssignee(p.name, "expert");
                    });
                }
                if ($scope.assignedNovices.length > 0) {
                    $scope.assignedNovices.forEach(p => {
                        $scope.assignedNovices = $scope.assignedNovices.filter(a => a.name !== p.name);
                        $scope.addAssignee(p.name, "novice");
                    });
                }

                $scope.teams.forEach(t => {
                    $scope.teamEdit = t;
                    t.members.forEach(m => {
                        $scope.removeMember(m.name);
                        self.addMember(m.name);
                    });
                    $scope.saveTeam();
                });
                console.log($scope.teams);
                $("#progress").css({
                    "visibility": "hidden"
                });
            });
    };

    /* Comment */
    /*############################################*/
    $scope.createComment = function () {
        $scope.comments.push({text: $scope.commentText, date: Date.now()});
        $('#comment').val('');
        $('#comment').trigger('autoresize');
    };

    /* Team functions */
    /*############################################*/
    $scope.editTeam = function (name) {
        if (name !== '') {
            var position = $scope.teams.findIndex(t => t.name === name);
            $scope.teamEdit = $scope.teams[position];
        } else {
            $scope.teamEdit = {name: '', members: []}
        }
    };

    $scope.addTeam = function (name) {
        var team = $scope.teams.filter(t => t.name === name)[0];
        for (var i in team.members) {
            $scope.addAssignee(team.members[i].name, 'expert');
        }
        Materialize.toast("Team " + name + " with " + team.members.length + " experts added", 4000);
    };

    $scope.removeTeam = function (name) {
        $scope.teams = $scope.teams.filter(t => t.name !== name);
    };

    $scope.saveTeam = function () {
        var position = $scope.teams.findIndex(t => t.name === $scope.teamEdit.name);
        if (position === -1) {
            $scope.teams.push($scope.teamEdit)
        } else {
            $scope.teams[position] = $scope.teamEdit;
        }
        $scope.teamEdit = '';
    };

    self.addMember = function (name) {
        $scope.teamEdit.members.push($scope.recommendedExperts.filter(p => p.name === name)[0]);
    };

    $scope.removeMember = function (name) {
        $scope.teamEdit.members = $scope.teamEdit.members.filter(m => m.name !== name);
    };
}]);